# salary-management-backend.md

**BINDING for all `salary-service/` work.** Read before touching a controller, an entity, or a
migration. Where this and a framework default disagree, this wins.

Spring Boot **4.0.8** · Java 17+ · Spring Security 7 · Spring Data JPA · Flyway · Neon PostgreSQL 17.

---

## 1. Package layout

```
com.acme.salaryos
├── SalaryOsApplication.java
├── config/            SecurityConfig, JacksonConfig, OpenApiConfig, AsyncConfig, SchedulingConfig
├── common/
│   ├── money/         Money (record), Currency, MoneyConverter, Rounding
│   ├── error/         ApiExceptionHandler, ProblemDetails, domain exceptions
│   ├── paging/        KeysetPage, Cursor, CursorCodec
│   └── time/          Clock provider (never LocalDate.now() inline — tests need to move it)
├── auth/              controller · service · domain(User, UserSession) · repository · dto · filter
├── employee/          controller · service · domain · repository · dto · spec
├── compensation/      controller · service · domain · repository · dto
│   └── effective/     EffectiveDating — the insert-only period logic, one class, heavily tested
├── band/              controller · service · domain · repository · dto
├── change/            controller · service · domain · repository · dto · ApplyDueChangesJob
├── analytics/         controller · service · query (native SQL) · dto
├── reference/         controller · service · domain · repository (departments, locations, levels…)
├── fx/                FxRateService, FxRateRepository, Normalizer
├── audit/             AuditService, AuditEvent, @Audited aspect, ReadAuditInterceptor
└── seed/              SeedRunner, generators/, SeedProperties
```

Layered strictly: `controller → service → repository`. A controller never touches a repository; a
repository never returns an entity to a controller. DTOs are Java `record`s and live in the module's
`dto` package. Entities never leave the service layer.

**Lombok is mandatory** — `@Getter`, `@Builder`, `@NoArgsConstructor(access = PROTECTED)`,
`@RequiredArgsConstructor`, `@Slf4j`. No hand-written getters, no `LoggerFactory.getLogger`. No
`@Setter` on entities: state changes go through named domain methods (`employee.terminate(date)`),
which is what makes the audit trail describe intent rather than field diffs.

---

## 2. Database

### 2.1 Neon specifics

- Connect through the **pooled** endpoint; `sslmode=require`. HikariCP `maximum-pool-size: 10`,
  `connection-timeout: 10s`, `max-lifetime: 5m` — Neon recycles connections, and a long
  `max-lifetime` produces intermittent, unreproducible failures in production only.
- `spring.jpa.properties.hibernate.default_schema: salary_schema`.
- **Every native query names its schema.** `hibernate.default_schema` rewrites entity-mapped SQL
  only; an unqualified table name in a `nativeQuery = true` string resolves against the connection's
  `search_path`, which on Neon is `public`. It will pass every local test and fail in production
  with "relation does not exist" — or worse, succeed against a stale `public` table. There is no
  embedded database in this build to catch it, so `NativeQuerySchemaQualificationTest` scans every
  `@Query(nativeQuery = true)` and every `JdbcTemplate` literal for an unqualified name and **fails
  the build**.
- Neon cold starts: the first request after idle can take a second. The health check must not treat
  that as down.

### 2.2 Flyway

`src/main/resources/db/migration/V<n>__<snake_case>.sql`. **A migration is immutable once
committed** — fix forward. Baseline set:

| Version | Contents |
|---|---|
| `V1` | Schema, extensions (`btree_gist`, `pg_trgm`, `citext`), `users`, `user_sessions`, `password_reset_tokens` |
| `V2` | Reference data: `countries`, `locations`, `departments`, `job_families`, `job_levels` |
| `V3` | `employees`, `employee_demographics` |
| `V4` | `salary_bands` |
| `V5` | `fx_rates` |
| `V6` | `compensation_records` (+ `validity` generated column, exclusion constraint), `compensation_components` |
| `V7` | `compensation_changes` (+ partial unique index for one open change) |
| `V8` | `audit_events` |
| `V9` | `employee_current_comp` projection |
| `V10` | Indexes from `Technical-Requirements.md §4.3` |
| `V11` | Seed-independent reference rows (currencies, reason codes) |

### 2.3 The exclusion constraint

```sql
CREATE TABLE salary_schema.compensation_records (
  id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id              uuid NOT NULL REFERENCES salary_schema.employees(id),
  effective_from           date NOT NULL,
  effective_to             date,
  validity                 daterange GENERATED ALWAYS AS
                             (daterange(effective_from, effective_to, '[)')) STORED,
  base_amount              numeric(15,2) NOT NULL CHECK (base_amount > 0),
  currency                 char(3)       NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  pay_frequency            text          NOT NULL,
  annual_base_amount       numeric(15,2) NOT NULL,
  normalized_annual_base   numeric(15,2) NOT NULL,
  base_currency            char(3)       NOT NULL,
  fx_rate_id               uuid          NOT NULL REFERENCES salary_schema.fx_rates(id),
  band_id                  uuid          REFERENCES salary_schema.salary_bands(id),
  compa_ratio              numeric(6,4),
  range_penetration        numeric(6,4),
  change_id                uuid,
  change_reason            text NOT NULL,
  superseded_by            uuid REFERENCES salary_schema.compensation_records(id),
  created_by               uuid NOT NULL,
  created_at               timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT comp_dates_ordered CHECK (effective_to IS NULL OR effective_to > effective_from),
  CONSTRAINT comp_no_overlap
    EXCLUDE USING gist (employee_id WITH =, validity WITH &&)
);
```

`compa_ratio` and `band_id` are **snapshots taken at write time**, not derived on read — a report of
2023 must use the 2023 band even after the band is re-versioned. Recomputing on read would silently
rewrite history every time someone edits a band.

---

## 3. Effective dating — `compensation/effective/EffectiveDating`

The one piece of logic the whole product rests on. All of it lives in one class, and it is the most
heavily tested code in the repository.

```java
/**
 * Applies a new base pay period. Insert-only: the open period is closed, never updated in place
 * beyond its end date, and a new row is inserted.
 */
@Transactional
public CompensationRecord apply(ApplyCommand cmd) {
    var open = repo.findOpenPeriod(cmd.employeeId())
        .orElseThrow(() -> new NoOpenPeriodException(cmd.employeeId()));

    if (!cmd.effectiveFrom().isAfter(open.getEffectiveFrom()))
        throw new BackdatedBeforeOpenPeriodException(...);   // corrections take a different path

    var band   = bands.findFor(employee.levelId(), employee.countryCode(), cmd.effectiveFrom());
    var rate   = fx.rateFor(cmd.currency(), baseCurrency, YearMonth.from(cmd.effectiveFrom()));
    var record = CompensationRecord.builder()
        .employeeId(cmd.employeeId())
        .effectiveFrom(cmd.effectiveFrom())
        .baseAmount(cmd.amount()).currency(cmd.currency())
        .annualBaseAmount(annualise(cmd.amount(), cmd.frequency(), employee.fte()))
        .normalizedAnnualBase(normalise(...)).fxRateId(rate.id())
        .bandId(band.map(Band::id).orElse(null))
        .compaRatio(band.map(b -> ratio(annual, b)).orElse(null))     // null, never 1.0
        .changeReason(cmd.reason()).changeId(cmd.changeId())
        .build();

    open.closeOn(cmd.effectiveFrom().minusDays(1));   // the only mutation permitted on this table
    repo.save(open);
    var saved = repo.save(record);
    projection.refresh(cmd.employeeId());
    audit.write(WRITE, "compensation_record", saved.getId(), open, saved);
    return saved;
}
```

Rules encoded here and asserted by tests:

1. Closing sets `effective_to = newFrom − 1 day`. Off-by-one here pays somebody twice for a day, or
   nothing at all, and no screen will show it.
2. The exclusion constraint is the backstop. **Do not catch and swallow its violation** — surface it
   as a 409 with the conflicting period in the `ProblemDetail`.
3. **No band means `compa_ratio = null`**, and the employee appears in the `NO_BAND` exception list.
   A default of 1.0 makes an unbanded employee look perfectly paid, which is exactly the case a
   compensation tool exists to surface.
4. Annualisation accounts for FTE and pay frequency; the multiplier lives in one place.
5. Corrections go through `correct(...)`, which inserts inside the existing period's dates, marks
   the superseded row `superseded_by`, and requires a note. The original row is never deleted.
6. `Clock` is injected. No `LocalDate.now()` inline anywhere in this package — every effective-dating
   test moves time.

---

## 4. Security

### 4.1 Filter chain

```java
@Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
  return http
    .csrf(csrf -> csrf
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())   // sos_csrf
        .ignoringRequestMatchers("/api/auth/login", "/api/auth/refresh"))
    .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
    .addFilterBefore(sessionCookieAuthFilter, UsernamePasswordAuthenticationFilter.class)
    .authorizeHttpRequests(a -> a
        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/actuator/health").permitAll()
        .anyRequest().authenticated())
    .exceptionHandling(e -> e
        .authenticationEntryPoint(problemDetail401)
        .accessDeniedHandler(problemDetail403))
    .build();
}
```

`SessionCookieAuthFilter` reads `sos_session`, validates signature/claims/expiry locally, checks the
`jti` against `user_sessions` for revocation, and sets one `SimpleGrantedAuthority("ROLE_" + role)`.
**One authority, no hierarchy** — see `CLAUDE.md §4.3`. Method security with `@PreAuthorize`
everywhere; `RolePermissionMatrixTest` walks every controller method, reads its `@PreAuthorize`, and
compares it to the table in `CLAUDE.md §7`, failing the build on a mismatch. A method with **no**
`@PreAuthorize` also fails it — silence is not a permission.

### 4.2 Passwords

`DelegatingPasswordEncoder` with `argon2` as the default id (`Argon2PasswordEncoder`, memory 19456
KiB, iterations 2, parallelism 1 — the OWASP baseline). The `{argon2}` prefix is stored so the
algorithm can be rotated later without a data migration.

### 4.3 Login response uniformity

Wrong password, unknown email, and locked account return the same status, the same body, and — this
is the part that gets missed — take the same time. Hash a dummy password when the user is not found,
or the response time answers the question the response body refuses to.

### 4.4 Refresh rotation

Each refresh mints a new token in the same `family_id` and revokes the old row. Presenting an
already-revoked refresh token revokes the **entire family** and forces re-login: that is the
signature of a stolen cookie, and the only safe response is to end every session it belongs to.

---

## 5. Money and FX

```java
public record Money(BigDecimal amount, String currency) {
    public Money {
        Objects.requireNonNull(amount); Objects.requireNonNull(currency);
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }
}
```

- `BigDecimal` everywhere, scale 2, `HALF_UP`. **No `double` in this codebase**, including in tests
  and in the seed generator.
- Jackson serialises `BigDecimal` **as a string** (`WRITE_BIGDECIMAL_AS_PLAIN` + string type) so
  JavaScript cannot silently round a large figure.
- **A `Money` never appears without its currency.** There is no DTO field named `amount` on its own.
- **Normalisation is pinned.** `fx_rates` holds one rate per (month, base, quote). A comp record
  stores both the normalised figure and the `fx_rate_id` used. Nothing recomputes a historical
  normalised figure — a report run twice returns the same number, which is a precondition for
  anyone trusting the first run.
- A missing rate for a month is a hard failure at write time (`MissingFxRateException`, 422), never
  a silent 1.0.

---

## 6. Analytics

Native SQL in `analytics/query/`, one class per question, each returning a projection record.
Aggregation happens in the database — the service never loads rows to sum them.

```java
// PayGapQuery — FR-6.4. Cohort = job level × country. Suppression is IN the query,
// not applied afterwards in Java, so there is no path that returns a small cohort at all.
private static final String SQL = """
    SELECT  jl.level_code,
            e.country_code,
            d.group_key,
            percentile_cont(0.5) WITHIN GROUP (ORDER BY c.normalized_annual_base) AS median,
            count(*) AS cohort_size
      FROM  salary_schema.employee_current_comp c
      JOIN  salary_schema.employees e  ON e.id = c.employee_id
      JOIN  salary_schema.job_levels jl ON jl.id = e.job_level_id
      JOIN  salary_schema.employee_demographics d ON d.employee_id = e.id
     WHERE  e.status = 'ACTIVE'
     GROUP  BY jl.level_code, e.country_code, d.group_key
    HAVING  count(*) >= :minCohortSize
    """;
```

- **`minCohortSize` is 5 and is not a request parameter.** It is a constant. A caller-supplied
  threshold is a caller-supplied way to identify one person's pay by their demographic group.
- The number of suppressed cohorts is returned so the UI can say what it is not showing — an
  omission the reader cannot see is worse than the omission.
- `employee_demographics` is joined **only** inside `analytics/query/`. It has no JPA relationship to
  `Employee`, so no fetch graph, no projection, and no lazy-loading accident can drag it into an
  employee response. `DemographicsIsolationTest` reflects over every class in every `dto` package
  outside `analytics` and fails the build if a field name matches the demographic set.
- Every analytics service returns the envelope from `Technical-Requirements.md §5.1`: as-at date, FX
  month, base currency, population, exclusions, suppressed count. **A figure without its basis does
  not leave this layer.**

---

## 7. Audit

- `@Audited` on service methods that mutate, handled by an aspect that writes actor, action, entity,
  before/after JSON, and request id.
- **Read auditing** (FR-7.2) is an interceptor on the endpoints that return individual pay data. For
  a list read it records the filter and the count, not 200 employee ids — the filter is what answers
  "what were they looking for".
- `audit_events` is append-only. The application's database role is granted `INSERT` and `SELECT`
  only; there is no update path to remove, and `AuditImmutabilityTest` asserts the grant.
- Audit writes happen in the **same transaction** as the change. An audit trail that can be missing
  the one row that mattered is not an audit trail.

---

## 8. Errors

Every failure is an RFC 7807 `ProblemDetail` with a `detail` written for a human — the UI shows it
directly, so "Constraint violation: comp_no_overlap" is not acceptable copy.

| Condition | Status | `detail` |
|---|---|---|
| Overlapping period | 409 | "This employee already has pay recorded from 2026-04-01. Choose a later effective date." |
| Second open change | 409 | "A change for this employee is already awaiting approval." (+ `changeId`) |
| Self-approval | 403 | "You proposed this change, so someone else has to approve it." |
| No FX rate | 422 | "No exchange rate for GBP→USD in March 2026. Add the rate and try again." |
| No band | 200 | Not an error — a null compa-ratio and a `NO_BAND` status. |
| Validation | 400 | Field-level `errors[]` the form maps back to inputs. |

---

## 9. Seeding — `seed/SeedRunner`

Runs under `--spring.profiles.active=seed`, after Flyway, refusing to run if `employees` is
non-empty unless `--app.seed.force=true`.

**Reproducible.** One `Random(APP_SEED_RANDOM_SEED)` — default `20260820` — threaded through every
generator. No `UUID.randomUUID()`, no `Instant.now()`, no `Math.random()` in the seed path: UUIDs are
derived deterministically from the seed and the row index, and dates are computed from a fixed
`SEED_AS_AT` date. Two runs on empty databases produce identical data, which is what lets a
screenshot in a document stay correct and a test assert a total.

**Shape:**

| Entity | Count | Notes |
|---|---|---|
| Countries | 8 | US, GB, DE, IN, SG, BR, PL, IE — deliberately spread across currency magnitudes so formatting and normalisation get exercised (INR and BRL break naive column widths) |
| Locations | 18 | 1–3 cities per country |
| Departments | 14 | With a two-level hierarchy |
| Job families / levels | 9 / 7 | `L1`–`L7` per family |
| Salary bands | ~500 | Per level × country, two versions each (2024, 2026) |
| FX rates | 72 months × 7 pairs | Random walk from a plausible start, not constant |
| **Employees** | **10,000** | Level distribution pyramid-shaped (L1–L2 ≈ 45%, L6–L7 ≈ 4%); managers resolved so the org chart is acyclic |
| Compensation records | ~40,000 | 3–7 periods each over six years, log-normal around band mid |
| Components | ~14,000 | Bonus targets at L4+, allowances in SG/IN |
| Changes | ~1,200 | Across all five statuses, including 60 awaiting approval so the queue is not empty |
| Users | 6 | One per role plus two spare HR Managers; passwords printed once to the console |

**Deliberate anomalies** (FR-8.4) — without these every screen looks empty of insight and no
reviewer can tell whether the analytics work:

- ~180 employees (1.8%) below band minimum, concentrated in two countries so the country breakdown
  has a real story.
- ~60 (0.6%) above maximum, mostly long-tenured L5+.
- 40 with a level/country combination that has no band, to exercise the `NO_BAND` path and the
  empty-band component state.
- One department carrying an adjusted gap of roughly 7% so the equity screen finds something, and
  several cohorts deliberately under five so the suppression counter is non-zero.
- A wide compa-ratio spread (0.72–1.28) so the histogram has shape.

**Performance.** `JdbcTemplate.batchUpdate` at 1,000 rows per batch, one transaction per entity type,
indexes created *after* the bulk inserts (`V10` runs last for a reason). Target under 90 seconds
against Neon; log the elapsed time per stage so a regression is visible.

---

## 10. Testing

| Layer | Tool | What |
|---|---|---|
| Unit | JUnit 5 + AssertJ | `EffectiveDating`, `Money`, annualisation, compa-ratio, FX normalisation |
| Slice | `@WebMvcTest` | Controller contracts, `ProblemDetail` shapes, CSRF, 401/403 |
| Integration | **Testcontainers Postgres 17** | Migrations, the exclusion constraint, keyset pagination, analytics SQL |
| Guard | Custom | `NativeQuerySchemaQualificationTest`, `DemographicsIsolationTest`, `RolePermissionMatrixTest`, `AuditImmutabilityTest`, `ProjectionConsistencyTest`, `ProposerIsNotApproverTest` |
| Performance | JMH-lite / timed integration | Employee list p95 against 10k seeded rows |

**No H2.** The schema uses `daterange`, `btree_gist`, `citext`, `pg_trgm`, generated columns, and
`percentile_cont`. An H2 suite would pass while production fails — which is the worst possible test
outcome, because it buys confidence in exchange for correctness.

The guard tests are not optional extras. Each one exists because the invariant it protects
(`CLAUDE.md §6`) **fails silently** — an unqualified query works locally, a demographic leak looks
like a normal field, a missing `@PreAuthorize` returns 200. If one of them fails, the test is right.

---

## 11. Observability

- `@Slf4j` everywhere; structured JSON logs; a `requestId` MDC value on every request and in every
  audit row.
- Micrometer timers on every analytics query and on `EffectiveDating.apply`.
- `/actuator/health` includes a database check tolerant of a Neon cold start; `/actuator/info`
  reports the Flyway version so a deployed environment can be identified from one call.
- **Never log a salary, a name, or an email at INFO.** Log the employee id. A log aggregator is a
  copy of your compensation data with none of its access controls.
