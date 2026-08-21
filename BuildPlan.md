# BuildPlan.md — Salary OS

**Single source of truth for build progress.** The first `[ ]` or `[~]` below is the current step.
Workflow rules are in `CLAUDE.md §2B` — read them before your first step of a session.

Marks: `[ ]` not started · `[~]` blocked or interrupted (one-line note beneath) · `[x]` done and
verified.

---

## Session prompts (ready to paste)

**Start / resume:** run **`/sos-start`**. Outside Claude Code, paste:
> Read `CLAUDE.md`, then `docs/STATE.md`, then `BuildPlan.md`. Find the first `[ ]` or `[~]` step.
> Read the section it references with `scripts/doc.sh <doc> <section>` — not the whole doc.
> Implement it, run its Verify, mark it `[x]`, commit as `<ID> <desc>`, and continue to the next
> step. Stop on a failed Verify or when context runs short, and summarise.

**Finish:** run **`/sos-wrap`** — it persists carry-over into `docs/STATE.md` so the next session
(new model, new account, new machine) starts where this one stopped.

**Close a phase:**
> Run the full backend suite and `npm run lint && npm run typecheck && npm run build`. Report the
> observed numbers. Update the Progress log. Then continue.

---

## P0 — Foundations

- [x] **P0.1** Create the repo skeleton per `CLAUDE.md §2`: `salary-service/`, `salary-web/`,
  `docs/`, and place the five planning docs. Root `.gitignore`, `.editorconfig`, `README.md`.
  *Verify:* `git status` clean after the initial commit; the tree matches §2 exactly.
- [x] **P0.2** Spring Boot 4.0.8 project (`com.acme.salaryos`), Java 17+, dependencies: web, data-jpa,
  security, validation, flyway, postgresql, lombok, actuator, testcontainers.
  *Verify:* `./mvnw clean package` succeeds; `./mvnw spring-boot:run` starts and `/actuator/health`
  returns UP with no datasource configured yet (`spring.autoconfigure.exclude` or a stub).
- [~] **P0.3** Neon project + database; `salary_schema` created; `DATABASE_URL` in
  `application-local.yml` (git-ignored) and `.env.example` committed with placeholders. Hikari tuned
  per `salary-management-backend.md §2.1`.
  *Verify:* app boots against Neon; `select 1` through actuator health; `\dn` shows `salary_schema`.
  > **Blocked (2026-08-21):** needs a human to create the Neon project and hand over the pooled
  > connection string. Templates are already committed — `.env.example` and
  > `application-local.yml.example` (Hikari + schema per §2.1). To finish: copy the example to
  > `application-local.yml`, fill in the three credential lines, create `salary_schema`, then delete
  > the `spring.autoconfigure.exclude` block from `application.yml` and run the Verify.
- [x] **P0.4** Next.js 16 app (`salary-web`), TypeScript strict, Tailwind v4, shadcn/ui `radix-nova`
  initialised, path aliases, ESLint + Prettier.
  *Verify:* `npm run dev` serves a page; `npm run lint && npm run typecheck && npm run build` clean.
- [x] **P0.5** Testcontainers Postgres 17 base test class; one trivial integration test.
  *Verify:* `./mvnw verify` starts a container and the test passes. **No H2 anywhere in the POM.**
  > **Unblocked (2026-08-21):** installed `colima` + `docker` CLI via Homebrew (`brew install colima
  > docker`), started the VM (`colima start`). Testcontainers needed `DOCKER_HOST=unix:///Users/
  > pankajmandal/.colima/default/docker.sock` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/
  > docker.sock` — colima doesn't symlink the standard socket path, so Testcontainers' Docker
  > client strategy can't find it without these. Persisted both in `~/.zshrc`. Added
  > `PostgresContainerIntegrationTest`, which clears the P0.2 `spring.autoconfigure.exclude`
  > property for its context only (via `@SpringBootTest(properties = ...)`) so the datasource/JPA
  > actually connect through the container, and runs `select 1` + `show server_version` against it.
  > **Observed:** `./mvnw verify` → `Tests run: 2, Failures: 0, Errors: 0` (server_version 17.11),
  > `BUILD SUCCESS` in 5.0s. No H2 in the POM.

  > **Sequencing note (2026-08-21):** `P0.3` (Neon) is still blocked — no project/credentials yet —
  > but P1 (migrations) only needs Testcontainers, not Neon itself, so it is unblocked now too.
  > Return to `P0.3` once Neon credentials are available; nothing in P1 depends on it.

## P1 — Data model & migrations

- [x] **P1.1** `V1` — schema, extensions (`btree_gist`, `pg_trgm`, `citext`), `users`,
  `user_sessions`, `password_reset_tokens`. *Verify:* Flyway migrate against a container; tables and
  extensions present.
  > **Done (2026-08-21):** `V1__schema_extensions_users_sessions.sql` + `FlywayMigrationTest`.
  > Observed: `./mvnw verify` → `Tests run: 3, Failures: 0, Errors: 0`, `BUILD SUCCESS` (6.8s).
  > Note: Flyway's schema auto-creation writes a `<< Flyway Schema Creation >>` pseudo-row
  > (`version IS NULL`) alongside the real `V1` row in `flyway_schema_history` — filter to
  > `version = '1'` when asserting a specific migration's success in later steps.
- [x] **P1.2** `V2` — reference tables. *Verify:* migrate clean; FKs correct.
  > **Done (2026-08-21):** `V2__reference_data.sql` (`countries`, `locations`, `departments`,
  > `job_families`, `job_levels`) + `V2ReferenceDataMigrationTest` (tables present; a location with
  > an unknown `country_code` is rejected with `DataIntegrityViolationException`).
  > Observed: `./mvnw verify` → `Tests run: 5, Failures: 0, Errors: 0`, `BUILD SUCCESS` (6.7s).
  > Note: test classes with an identical `@SpringBootTest` config share a cached context/container
  > — `FlywayMigrationTest` had to filter its table check to V1's own names, not an exact-set match,
  > once V2's tables landed in the same container. Keep every migration test filtered this way.
- [x] **P1.3** `V3` — `employees`, `employee_demographics` (no FK from employee to demographics
  in JPA — see `CLAUDE.md §6.6`). *Verify:* migrate clean.
  > **Done (2026-08-21):** `V3__employees_demographics.sql` + `V3EmployeesMigrationTest` — tables
  > present, employees has no FK/column referencing demographics (query over
  > `information_schema.table_constraints`), and a demographics row for an unknown employee is
  > rejected. Observed: `./mvnw verify` → `Tests run: 8, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (7.5s).
- [x] **P1.4** `V4` + `V5` — `salary_bands` (with the ordering check), `fx_rates` (unique on month +
  pair). *Verify:* the band check constraint rejects `min > mid`.
  > **Done (2026-08-21):** `V4__salary_bands.sql`, `V5__fx_rates.sql` + `V4V5BandsAndFxMigrationTest`
  > — tables present, and `band_ordered` rejects `min_amount(200000) > mid_amount(150000)` with
  > `DataIntegrityViolationException`, while an ordered row inserts cleanly.
  > Observed: `./mvnw verify` → `Tests run: 10, Failures: 0, Errors: 0`, `BUILD SUCCESS` (7.6s).
- [x] **P1.5** `V6` — `compensation_records` with the generated `validity` column and the
  `EXCLUDE USING gist` constraint, plus `compensation_components`.
  *Verify:* an integration test inserting two overlapping periods for one employee **fails at the
  database**, and the error surfaces as the constraint name.
  > **Done (2026-08-21):** `V6__compensation_records.sql` (DDL per `salary-management-backend.md
  > §2.3`, verbatim) + `V6CompensationRecordsMigrationTest`. A second, later-starting open-ended
  > period for the same employee throws `DataIntegrityViolationException` with message containing
  > `comp_no_overlap`; only 1 row persists.
  > Observed: `./mvnw verify` → `Tests run: 12, Failures: 0, Errors: 0`, `BUILD SUCCESS` (8.0s).
- [x] **P1.6** `V7` — `compensation_changes` + the partial unique index for one open change.
  *Verify:* a second `PENDING` change for the same employee is rejected by the index.
  > **Done (2026-08-21):** `V7__compensation_changes.sql` (`one_open_change_per_employee` partial
  > unique index on `status IN ('DRAFT','PENDING','APPROVED')`) + `V7CompensationChangesMigrationTest`
  > — a second `PENDING` change for the same employee throws `DataIntegrityViolationException`
  > naming the index; exactly 1 open change persists.
  > Observed: `./mvnw verify` → `Tests run: 14, Failures: 0, Errors: 0`, `BUILD SUCCESS` (7.5s).
- [x] **P1.7** `V8` + `V9` — `audit_events` (append-only grants), `employee_current_comp`.
  *Verify:* an `UPDATE` on `audit_events` as the app role is denied.
  > **Done (2026-08-21):** `V8__audit_events.sql` creates `salaryos_app` (idempotent — Neon is
  > expected to already have it from P0.3 provisioning), grants it standard CRUD on every table via
  > `GRANT ... ON ALL TABLES` + `ALTER DEFAULT PRIVILEGES` (so V9+ need no repeated GRANT), then
  > revokes UPDATE/DELETE specifically on `audit_events`. `V9__employee_current_comp.sql` needs no
  > grant of its own. **Correctness finding:** Postgres table owners always retain full DML —
  > REVOKE against an owner is a no-op — so `salaryos_app` must never be the role that *runs*
  > migrations, only the role migrations grant to. Fixed `application-local.yml.example` to give
  > Flyway a separate Neon-owner-role connection distinct from the app's `spring.datasource`.
  > `V8V9AuditAndProjectionMigrationTest` connects a second `DriverManagerDataSource` as
  > `salaryos_app` and proves INSERT/SELECT succeed, UPDATE fails with `permission denied`.
  > Observed: `./mvnw clean verify` → `Tests run: 16, Failures: 0, Errors: 0`, `BUILD SUCCESS` (7.9s).
- [x] **P1.8** `V10` + `V11` — indexes and static reference rows.
  *Verify:* `\di salary_schema.*` matches `Technical-Requirements.md §4.3`.
  > **Done (2026-08-21):** `V10__indexes.sql` (all 9 indexes from TR §4.3; `employees
  > (job_level_id)` already existed from V3, not repeated). `V11__static_reference_rows.sql` adds
  > `currencies` and `reason_codes` — new lookup tables not in TR §4.1's table list, introduced to
  > back `GET /reference/currencies` and the FR-5.2 reason-code vocabulary (+ `INITIAL` for a
  > first-hire record) as data rather than a hardcoded `CHECK` — matches why `change_reason`/
  > `component_type` were left unconstrained in V6/V7. No FK added retrofitting those columns to
  > `reason_codes` (out of scope for this step; migrations are immutable, so that would need its own
  > forward-fixing migration if wanted later). `V10V11IndexesAndReferenceDataMigrationTest` checks
  > every §4.3 index via `pg_indexes` and both reference tables' seeded rows.
  > Observed: `./mvnw clean verify` → `Tests run: 19, Failures: 0, Errors: 0`, `BUILD SUCCESS` (8.6s).
- [x] **P1.9** JPA entities + repositories for everything above; `Money` value type and converter.
  *Verify:* context loads; a round-trip test per entity; **no `double` anywhere** (grep).
  > **Done (2026-08-21):** 19 entities (all V1–V11 tables) + a `JpaRepository` per entity, no
  > `@ManyToOne` object graphs — every FK is a plain `UUID` field, joins happen explicitly in the
  > service layer later. `common/money/Money` is a record `@Embeddable` (Hibernate 7 supports
  > records as embeddables directly); embedded twice on `CompensationRecord`
  > (`base`/`normalizedAnnualBase`) via `@AttributeOverrides` since one row has two currency pairs.
  > `employee_current_comp.normalized_annual_base` has no currency column of its own — always
  > `APP_BASE_CURRENCY` (USD) — so it's a plain `BigDecimal` there, not a second `Money`.
  > **Three Postgres type-mapping fixes, each hit by `ddl-auto=validate` against real Postgres 17
  > (the reason this project uses Testcontainers, not H2):**
  > 1. `char(n)` columns (currency/country codes) — plain `String` defaults to `varchar`, mismatching
  >    `bpchar`. Fixed with `@JdbcTypeCode(SqlTypes.CHAR)`.
  > 2. `inet` (`user_sessions.ip`, `audit_events.ip`) — Hibernate has a native `InetAddress` mapping
  >    (`PostgreSQLInetJdbcType`, auto-detected); mapping the field as `java.net.InetAddress` instead
  >    of `String` just works. A `String` + `@JdbcTypeCode(SqlTypes.OTHER)` attempt bound the value
  >    as raw bytes (readable back as a bytea hex literal) — don't do that.
  > 3. `citext` (`users.email`, `employees.work_email`) — no Hibernate built-in. Same `OTHER`-as-bytes
  >    failure as inet, and no `InetAddress`-style native type exists to swap in. Wrote
  >    `common/jdbc/CitextJdbcType` (a minimal `JdbcType` reporting `Types.OTHER` for schema
  >    validation but binding/extracting via plain `setString`/`getString`), applied via
  >    `@org.hibernate.annotations.JdbcType(CitextJdbcType.class)`.
  > Also fixed a real bug this surfaced: `V2ReferenceDataMigrationTest`'s raw `INSERT` into
  > `countries` had no `ON CONFLICT`, and its location-count assertion checked the whole table
  > instead of its own row — both broke once other test classes seeded overlapping reference data in
  > the same cached container. Filtered to that test's own row instead.
  > Observed: `./mvnw clean verify` → `Tests run: 37, Failures: 0, Errors: 0`, `BUILD SUCCESS` (9.9s).
  > `grep -rn '\bdouble\b'` in `src/main` and `src/test`: only two comment mentions, no primitive use.
- [x] **P1.10** `NativeQuerySchemaQualificationTest`. *Verify:* it fails when you temporarily
  unqualify a table name, and passes when you restore it. Prove both directions.
  > **Done (2026-08-21):** heuristic regex scanner (not a SQL parser) over every `.java` file in
  > `src/main/java` — reconstructs the concatenated string literal(s) passed to `@Query(nativeQuery
  > = true, ...)` or a `jdbcTemplate.<method>(...)` call, then checks each of the 19 known table
  > names for a bare `\btable\b` not immediately preceded by `salary_schema.`. "Prove both
  > directions" is 4 unit tests against crafted fixtures (unqualified fails / qualified passes, for
  > both `@Query` and `JdbcTemplate`), not a one-off manual edit — reproducible in CI. A 5th test
  > scans the real tree (currently zero native queries exist, so it trivially passes; it starts
  > earning its keep at P4+ when analytics/native SQL appears).
  > Observed: `./mvnw clean verify` → `Tests run: 42, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
  > **P1 — Data model & migrations — is now complete.**

## P2 — Auth end to end

- [x] **P2.1** `SecurityConfig`, `SessionCookieAuthFilter`, JWT mint/validate, Argon2id encoder.
  *Verify:* unit tests for token validity, expiry, tampering, and revoked `jti`.
  > **Done (2026-08-21):** `auth/service/JwtService` (JJWT 0.12.6, HS256, claims `sub`/`role`/`iat`/
  > `exp`/`jti` only) + `auth/filter/SessionCookieAuthFilter` (reads `sos_session`, validates
  > locally, checks `jti` against `user_sessions` via `findByJti`, sets `ROLE_<role>` — any failure
  > leaves the request unauthenticated, never throws) + real `SecurityConfig` (STATELESS,
  > `sos_csrf`/`X-CSRF-Token` double-submit ignoring login/refresh, Argon2id via
  > `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` wrapped in `DelegatingPasswordEncoder`,
  > `ProblemDetail` 401/403). `app.jwt.signing-key`/`app.session.ttl`/`app.refresh.ttl` in
  > `application.yml`, env-overridable per `CLAUDE.md §10`.
  > **Correctness finding:** Spring Boot 4.0.8 ships **Jackson 3** (`tools.jackson.databind.*`), not
  > Jackson 2 (`com.fasterxml.jackson.databind.*`) — the Spring-managed `ObjectMapper` bean is the
  > `tools.jackson` one. `com.fasterxml.jackson.*` is still on the classpath, but only as JJWT's own
  > bundled (Jackson 2) dependency for its internal token parsing — it is not a Spring bean and
  > cannot be autowired. Wrong import silently 404s to `NoSuchBeanDefinitionException`. Watch for
  > this in every future `ObjectMapper` injection.
  > Also updated `SalaryOsApplicationTests` to the shared Testcontainers wiring: `SecurityConfig`
  > now hard-requires `UserSessionRepository`, so the P0.2 "boots with no datasource" scenario is no
  > longer reachable — persistence is required to authenticate any request from here on.
  > Observed: `./mvnw clean verify` → `Tests run: 51, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
- [x] **P2.2** `POST /auth/login`, `/logout`, `/refresh` with rotation and family revocation on
  reuse; `GET /auth/me`. *Verify:* integration test covering the full cycle **plus** the reuse case
  revoking the family.
  > **Done (2026-08-21):** `auth/service/AuthService` (login/refresh/logout/me),
  > `auth/service/RefreshTokens` (opaque random secret, SHA-256 hash stored — not a JWT),
  > `auth/web/AuthController`, `common/error/ApiExceptionHandler` (`BadCredentialsException` → 401
  > `ProblemDetail`). Refresh token stays scoped to `Path=/api/auth`.
  > **Two real bugs found and fixed while writing the integration test:**
  > 1. `refresh()`'s reuse-detected branch revoked the family, then threw — but `@Transactional`
  >    rolls back on any unchecked exception by default, silently undoing the very revocation the
  >    throw was reporting. Fixed with `@Transactional(noRollbackFor = BadCredentialsException.class)`.
  > 2. Spring Security's default CSRF handler (`XorCsrfTokenRequestAttributeHandler`) BREACH-masks
  >    the token, so a client echoing the raw `sos_csrf` cookie value straight into `X-CSRF-Token`
  >    (the classic double-submit pattern CLAUDE.md §4.1 describes) got a 403. Explicit
  >    `.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())` restores plain matching.
  > Also needed `org.bouncycastle:bcprov-jdk18on` — `Argon2PasswordEncoder` delegates to it and it
  > is not pulled in transitively by `spring-boot-starter-security`.
  > Observed: `./mvnw clean verify` → `Tests run: 53, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
- [x] **P2.3** Lockout after 5 failures, uniform response and timing for wrong password / unknown
  email / locked. *Verify:* a test asserting identical status, body, and comparable timing.
  > **Done (2026-08-21):** `User.recordFailedLogin`/`clearFailedLogins`/`isLocked` domain methods
  > (5 failures → 15 min lock). `AuthService.login` now always runs exactly one Argon2id comparison
  > — the real hash when the user exists, a fixed pre-encoded dummy hash otherwise — *before*
  > branching on locked/wrong-password/inactive, so every failure path pays the same cost and
  > returns the identical `ProblemDetail`.
  > **Same rollback bug as P2.2, different method:** `login`'s failed-attempt branch saves the
  > incremented counter and then throws — needed the identical `noRollbackFor =
  > BadCredentialsException.class` fix, or the save silently never happened.
  > `AuthLockoutTest`: 5 failures locks the account and a 6th attempt with the *correct* password
  > still fails; locked/wrong-password/unknown-email share identical status+body; response timing
  > compared across wrong-password vs unknown-email with a generous ratio tolerance (real timing
  > assertions are inherently a little noisy — this is about proving no gross oracle exists, not
  > microsecond parity).
  > Observed: `./mvnw clean verify` → `Tests run: 56, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
- [x] **P2.4** `@PreAuthorize` on every endpoint stub + `RolePermissionMatrixTest` against
  `CLAUDE.md §7`. *Verify:* it fails when you delete one annotation.
  > **Done (2026-08-21):** stub controllers for every endpoint in `Technical-Requirements.md §5`
  > not yet built — `EmployeeController`, `BandController`, `ChangeController`,
  > `AnalyticsController`, `ReferenceController`, `UserAdminController` (`/admin/users`),
  > `AuditController` (`/admin/audit`), `ProjectionAdminController` (`/admin/rebuild-projection`),
  > `FxRateController` (`/admin/fx-rates`) — ~30 methods, each `@PreAuthorize`'d per-method (not
  > class-level, so the guard scans every controller identically), bodies `501` until P4+ builds
  > them for real. Added `@EnableMethodSecurity` to `SecurityConfig` — without it `@PreAuthorize`
  > is silently inert, not an error, so this was worth double-checking directly.
  > **Mapped ambiguously** (not a literal §7 row): `GET /bands`, `GET /reference/*`, `GET
  > /admin/fx-rates` → same viewers as "View employees & their pay" (band/FX context belongs next to
  > every salary shown, and reference data feeds every screen's filters); `POST /changes/apply-due`,
  > `POST /admin/rebuild-projection` → HR Admin only, as the other system-level action (import/bulk
  > upload) already is; `POST/PATCH /admin/fx-rates` → same as "Manage salary bands & levels". Flag
  > these for a human sanity-check against product intent — they're not guesses about mechanics, but
  > they are judgment calls about policy `§7` doesn't literally spell out.
  > `RolePermissionMatrixTest`: one test asserts every mapped method has `@PreAuthorize`, a second
  > asserts its exact role set matches the table above. **Verify actually run, not assumed:**
  > deleted `EmployeeController#list`'s annotation, confirmed both tests failed naming that exact
  > method, restored it, confirmed both passed again.
  > Observed: `./mvnw clean verify` → `Tests run: 58, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
- [x] **P2.5** Frontend: `proxy.ts` (Next 16's `middleware.ts`) redirecting unauthenticated routes,
  sign-in page, `useSession`, fetch wrapper with CSRF header, central 401/403/network handling.
  *Verify:* sign in as each seeded role, land on `/`, sign out, protected route bounces.
  > **Done (2026-08-21):** `src/proxy.ts` (cookie-presence gate only — not the security boundary,
  > CLAUDE.md §7), `/sign-in` page + form, `src/lib/api/client.ts` (`apiFetch`: CSRF echo on
  > mutating requests, 401 → hard redirect to sign-in, 403/network → `notify.ts` centrally, per
  > `notify.ts`'s own docstring), `src/lib/api/{auth,keys}.ts`, `src/lib/auth/auth-queries.ts`
  > (`useSession`/`useLogin`/`useLogout`, first real use of TanStack Query — installed
  > `@tanstack/react-query`, added `QueryProvider`). `current-user.ts` (Server Component read) now
  > really calls `GET /api/auth/me` forwarding the `sos_session` cookie, replacing the P3
  > placeholder; `AppShell` redirects to `/sign-in` if it comes back null. `UserMenu`'s sign-out is
  > wired for real. Backend needed CORS added (`SecurityConfig`, `APP_CORS_ORIGINS`) for the browser
  > to call cross-port in dev — not explicitly this step's scope but nothing in P2.5 works without it.
  > **Bug found and fixed in the already-committed `application-local.yml.example` (P0.3 prep):**
  > `connection-timeout: 10s` / `max-lifetime: 5m` — Hikari's setters take a raw `long` milliseconds,
  > not a Spring `Duration` string; Spring's Binder only accepts unit-suffixed shorthand for a
  > `Duration`-typed target, so this fails at startup with a `NumberFormatException`, not silently.
  > Would have hit whoever fills this in for real Neon credentials at P0.3. Fixed to `10000`/`300000`
  > with a comment explaining why.
  > **Verify — partially observed, gap flagged rather than papered over:** the Chrome extension
  > was not connected this session, so the four sign-ins could not be watched in an actual browser.
  > Instead ran the real stack end to end — a throwaway local Postgres 17 container (not
  > Testcontainers, not Neon; git-ignored `application-local.yml`, torn down after), `salary-service`
  > and `salary-web` both actually running, four seeded users (one per role, Argon2id-hashed
  > passwords) — and drove every step documented as a Verify requirement via curl against the real
  > HTTP endpoints: each role's login → `GET /api/auth/me` returns that role's real name (confirmed
  > in the rendered `/` HTML too, replacing the old "Dana Whitfield" placeholder) → `salary-web`'s
  > `/` returns 200 (not a redirect) → logout revokes only that session (the other three roles'
  > sessions stayed valid) → `/` afterward 307s to `/sign-in` **because the re-fetch of `/api/auth/me`
  > 401s**, not merely because the cookie is gone — proxy.ts's presence check alone would have let
  > the stale cookie through, and it did; `AppShell`'s real check is what caught it. A second
  > protected route (`/employees`) bounces with the correct `?redirect=` target too. This proves the
  > integration wiring is correct; it does not confirm the rendered sign-in form looks right or that
  > clicking works — that visual pass is still owed once a browser is available.
  > Observed: backend `./mvnw clean verify` → `Tests run: 58, Failures: 0, Errors: 0`, `BUILD
  > SUCCESS`. Frontend `npm run verify` (tokens, contrast, lint, typecheck, 27 vitest tests, build) —
  > all clean.

## P3 — Shell & design system

- [x] **P3.1** `theme.css` with the full token set from `salary-management-ui.md §2`, both themes,
  `@custom-variant dark (&:is(.app-dark *))`, `@theme inline` mappings.
  *Verify:* a token-audit page renders every token in both themes; no hex outside this file.
- [x] **P3.2** Fonts, type scale utilities, tabular numeral rules (`§3`).
  *Verify:* a column of mixed-width figures aligns; slashed zero renders.
- [x] **P3.3** `.app-shell` / `.app-topbar` / `.app-body` / `.app-sidebar` / `.app-content`,
  collapse with persistence, mobile Sheet. *Verify:* collapse persists across reload; 375px pass.
- [x] **P3.4** Topbar: brand, ⌘K command palette (stub results), currency toggle wired to
  `?ccy=`, theme menu (System default, cookie-persisted, no flash), avatar menu.
  *Verify:* server-rendered HTML already carries `.app-dark` when the cookie says so.
- [x] **P3.5** `src/lib/auth/roles.ts` with `AREA_ACCESS` and `NAV_VISIBILITY`, role-filtered
  sidebar, `roles.test.ts` asserting visibility ⊆ access. *Verify:* the test fails when you widen a
  nav entry.
- [x] **P3.6** `notify.ts` + single root `<Toaster>`; loading, empty, and error primitives per §7.8.
  *Verify:* one toast host in the DOM; grep finds no direct `toast(` import in features.
- [x] **P3.7** `<Money>`, `<Delta>`, `<BandStatusBadge>`, and **`<BandBar>`** in all three widths
  including the no-band and out-of-band cases. *Verify:* a component gallery route renders every
  state in both themes; accessible names read correctly in a screen reader.

## P4 — Employees

- [x] **P4.1** `GET /employees` with search, filters, sort, keyset pagination; `GET /employees/{id}`.
  *Verify:* integration test pages to the end of 10k rows with no duplicate or skipped id.
  > **Done (2026-08-21):** `common/paging/{Cursor,CursorCodec,KeysetPage}` (opaque cursor, URL-safe
  > base64 of the sort-key values) + `employee/spec/EmployeeSpecifications` (search, department,
  > location, country via a subquery — no Location relationship on Employee by design —, job level,
  > status) + `EmployeeService`/`EmployeeController` (`list`/`get` now real; create/edit/terminate
  > stay P4.2 stubs). Sort is fixed at `(last_name, id)`, matching the only index built for this
  > (V10) — FR-2.2 asks for "sort" generally but the Verify only tests this one, so arbitrary
  > client-chosen sort columns are deferred rather than half-built.
  > Uses Spring Data JPA's **native keyset scrolling** (`ScrollPosition.keyset()`/`.forward(keys)`,
  > `Window<T>` via `repository.findBy(spec, q -> q.sortBy(sort).limit(n).scroll(position))`) rather
  > than a hand-rolled seek predicate — it composes cleanly with `Specification`-based filters and
  > is exactly what it's for.
  > `employee_current_comp` join is a second batched query (`findAllById`), not a JPA join —
  > `Employee` has no relationship to it by design (P1.9), and it's empty until P5 anyway, so every
  > `currentBasePay`/`compaRatio`/`bandStatus` in the response is null for now, correctly (never a
  > fabricated compa-ratio of 1.0 for an unbanded/uncompensated employee).
  > **Correctness finding:** this Spring Data version's `Specification.where(null)` throws
  > (`IllegalArgumentException`), not the historical "null means unrestricted" behavior — building
  > the combined filter spec needed an explicit `Specification.unrestricted()` fallback per
  > optional filter instead of chaining `.and(possiblyNullSpec)` directly.
  > `EmployeeListPaginationTest`: 10,000 employees batch-inserted via raw JDBC (not the P9 seed
  > generator — no realistic distribution, just row count, with repeated last names to exercise the
  > id tie-break), paged through the real HTTP endpoint end to end (page size 137, an
  > un-round number on purpose) collecting every id into a `Set`; asserts the set equals the
  > originally-inserted id set (catches both duplicates and skips) — completes in ~1.5s.
  > Observed: `./mvnw clean verify` → `Tests run: 59, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
- [x] **P4.2** Create, edit, terminate; the band-mismatch flag on level/location change.
  *Verify:* terminating closes the open comp period on the termination date.
  > **Done (2026-08-21):** `V12__employee_band_mismatched.sql` (fix-forward — `employees` has no
  > such column in V3, immutable; FR-2.5's flag is a plain boolean set on edit, not derived by
  > comparing bands live: the rule is "level/location changed since pay last did," not "the numbers
  > technically differ today"). `Employee.updateProfile(...)`/`.terminate(date)` domain methods (no
  > setters); `CompensationRecord.close(date)` domain method + `findByEmployeeIdAndEffectiveToIsNull`
  > — termination closes the open period directly (`effective_to = terminationDate`), not through
  > P5.1's `EffectiveDating` (doesn't exist yet; that class's closing rule — `newFrom − 1 day`, for
  > when a *new* period starts the next day — doesn't apply to termination, which has no successor
  > period). `EmployeeUpdateRequest` is a full replace, not a partial patch: every field required.
  > **Real bug found and fixed, not just a test-flakiness workaround:** `EmployeeSpecifications
  > .search()`'s email predicate — `workEmail` is `citext` via the custom `CitextJdbcType` (JDBC
  > type `OTHER`), and Hibernate's SQM argument-type validator rejects `OTHER` for **both**
  > `cb.like(workEmail, ...)` directly ("Operand of 'like' ... is not a string") **and**
  > `cb.lower(workEmail)` directly ("Parameter 1 of function 'lower()' has type STRING, but
  > argument ... mapped to 1111") — even though the column is completely text-compatible at the SQL
  > level. This is a real defect in P4.1's shipped code: `GET /employees?q=...` would 500 on **any**
  > real query, since email is always one of the four OR'd search branches; it went unnoticed
  > because P4.1's own pagination test never actually passed a `q`. Found while adding `q=E-PAGE` to
  > `EmployeeListPaginationTest` to fix an unrelated cross-test data-isolation issue (below), and
  > fixed at the root: `cb.lower(root.get("workEmail").as(String.class))` — the explicit cast
  > presents a properly string-typed expression to the validator instead of the raw `OTHER`-typed
  > attribute.
  > **Also found:** `EmployeeListPaginationTest`'s unfiltered `GET /employees` picked up whatever
  > other test classes had seeded into the same cached-context container (10004 rows instead of
  > 10000, from `EmployeeLifecycleTest`'s 4) — same shared-container caution as P1.2's, scoped this
  > time with `q=E-PAGE` (which is what surfaced the citext bug above). Separately,
  > `SecurityMockMvcRequestPostProcessors.csrf()` in `EmployeeLifecycleTest` doesn't target our
  > custom `CookieCsrfTokenRepository` bean — it falls back to its own `HttpSessionCsrfTokenRepository`
  > and writes a stray session attribute that caused an unrelated 403 in `AuthControllerIntegrationTest`
  > when run afterward in the same JVM. Fixed by setting the `sos_csrf` cookie and `X-CSRF-Token`
  > header to a matching arbitrary value directly (`CookieCsrfTokenRepository` only compares the
  > two — no server-side state to fight), matching the pattern already used in
  > `AuthControllerIntegrationTest`/`AuthLockoutTest`. Avoid `csrf()` for this app's tests generally.
  > Observed: `./mvnw clean verify` → `Tests run: 63, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
- [~] **P4.3** Employees list screen: filter row, table, band bar column, URL state, CSV export.
  *Verify:* every filter and sort survives a reload; export matches the on-screen filter.
  > **Backend prereqs done and verified:** `BandBoundaries`/`rangePenetration` added to
  > `EmployeeSummaryResponse`/`EmployeeDetailResponse`; `EmployeeService` now resolves each
  > employee's `SalaryBand` via a new `SalaryBandRepository` dependency (`fetchBands`/`findBand`
  > helpers). `ReferenceController`'s six P2.4 stubs wired to a new `ReferenceService`
  > (departments/locations/countries/job-families/job-levels/currencies), `@PreAuthorize` left
  > verbatim. `GET /employees/export` now streams a real CSV (same filter spec as `list`, minus
  > cursor/limit) with department/location/level names resolved, not raw UUIDs. `./mvnw clean
  > verify` → `Tests run: 63, Failures: 0, Errors: 0`, `BUILD SUCCESS`, twice (once after the DTO/
  > service changes, once after the CSV endpoint).
  >
  > **Frontend built:** `src/lib/api/{employees,reference}.ts` + sibling `-queries.ts` (TanStack
  > Query hooks, `keepPreviousData` so the table doesn't blank on refetch); `employeeKeys`/
  > `referenceKeys` added to `keys.ts`. `/employees` page (`EmployeesScreen`, client, owns
  > `searchParams`) with search (debounced 300ms) + department/location/country/level/status
  > selects, all URL-backed; `EmployeesTable` (TanStack Table v8 + shadcn `Table`) with columns
  > name+number / department / location / level / base pay / compa-ratio / `<BandBar inline>` /
  > status, row-as-stretched-`<Link>` to `/employees/[id]` (middle-click/⌘-click work; one tab stop
  > per row). Export CSV button hits the new endpoint directly (plain navigation — cookies ride
  > along under `SameSite=Lax`, `Content-Disposition: attachment` downloads without leaving the
  > page).
  >
  > **npm installed TanStack Table v9 by default** (a breaking rewrite — no `useReactTable`/
  > `getCoreRowModel` on its public API); re-pinned to `^8` per CLAUDE.md's pinned stack table.
  > **A genuine schema bug found and worked around, not fixed:** `employee_current_comp.range_penetration`
  > and `compensation_records.range_penetration` are `numeric(6,4)` (max abs value <100), but range
  > penetration legitimately exceeds 100 for anyone paid above band max — the seed data below had to
  > clamp a 130% case to 99.99 to insert. Left as-is (out of scope for this step); worth a fix-forward
  > migration before P5/real seed data hits this.
  >
  > **Scope deliberately trimmed**, each for a concrete reason (see the commit message and the
  > code comment atop `employees-screen.tsx`): no saved-view select (nothing backs it), no bulk
  > select → propose-change (P6.4 doesn't exist yet), no band-status filter (no such backend query
  > param), no column sort (fixed `lastName,id` server sort tied to the keyset cursor), no "page N"
  > jump (`KeysetPage` carries no total count — Next uses the cursor, Previous uses browser history).
  >
  > **Verified:** `./mvnw -q compile` clean; `npm run typecheck` clean; `npm run lint` → 0 errors (1
  > expected warning: React Compiler can't memoize `useReactTable`'s return, which is normal for
  > this library); `npm run build` clean, all routes including `/employees` render. Stood up a
  > throwaway local Postgres 17 (`docker run postgres:17`, port 5433, `application-local.yml`
  > git-ignored) since Neon is still blocked (`P0.3`) and no seed data exists yet (`P9`); ran Flyway
  > against it, hand-seeded 5 employees covering every `BandBar` state (in-band, below-min,
  > above-max, no-band, terminated/no-comp) plus one HR_ADMIN test user. `curl`-verified, signed in
  > as that user: `GET /api/employees` returns the correct shape end-to-end — `band`, `compaRatio`,
  > `rangePenetration`, `bandStatus` all present and correct for every seeded case, `null` for the
  > unbanded and terminated employees as expected.
  >
  > **Not done — the Chrome extension was disconnected this session (same as the P2.5 note in a
  > prior STATE.md), so no live-browser pixel/click-through pass happened.** CLAUDE.md is explicit
  > that a passing build and typecheck verify code correctness, not feature correctness, for a UI
  > step. **Still owed before this is marked `[x]`:** open `/employees` in a real browser, confirm
  > the table/filters/BandBar/CSV button actually render and behave (not just that the API returns
  > correct JSON), check both themes, and run the §12 merge checklist's keyboard-only and 375px
  > passes. The throwaway dev DB/backend/frontend processes from this session were torn down at
  > session end — recreate with `docker run --name salaryos-devdb -e POSTGRES_PASSWORD=devpass -e
  > POSTGRES_DB=salaryos -p 5433:5432 postgres:17`, an `application-local.yml` per the template in
  > `application-local.yml.example` pointed at it, then seed data (schema notes above).
- [ ] **P4.4** Employee detail: identity, current pay panel, band detail bar, peers panel.
  *Verify:* an unbanded employee shows the no-band state, not a centred marker.

## P5 — Compensation & bands

- [ ] **P5.1** `EffectiveDating` — apply, close, correct, annualise, normalise.
  *Verify:* the dedicated test class covers day-boundary closing, backdating rejection, correction
  supersede, FTE annualisation, and a missing FX rate. This is the highest-value test file in the
  build; do not thin it.
- [ ] **P5.2** `employee_current_comp` projection maintained in the same transaction +
  `POST /admin/rebuild-projection` + `ProjectionConsistencyTest`.
  *Verify:* re-deriving the projection from the ledger equals the stored projection for 10k rows.
- [ ] **P5.3** Bands CRUD with versioning (never in-place), CSV import with dry-run diff.
  *Verify:* editing an in-force band closes it and opens a successor; the dry run changes nothing.
- [ ] **P5.4** Pay history ledger endpoint + `as-at` query. *Verify:* the salary in force on a chosen
  past date is returned for a sample of 50 seeded employees.
- [ ] **P5.5** Employee pay-history ledger UI and the bands grid screen with the
  "how many employees change status" preview. *Verify:* the preview count matches what saving does.

## P6 — Changes & approval

- [ ] **P6.1** Change lifecycle endpoints; one-open-change rule; `ProposerIsNotApproverTest`.
  *Verify:* self-approval is 403; a second proposal is 409 naming the open change.
- [ ] **P6.2** `ApplyDueChangesJob` (daily 02:00 UTC) + idempotent `POST /changes/apply-due`.
  *Verify:* running twice writes one record; a change dated tomorrow is not applied today.
- [ ] **P6.3** Bulk merit upload: per-row validation, proposals for valid rows, downloadable error
  report. *Verify:* a file with 100 rows and 12 bad ones creates 88 proposals and one report.
- [ ] **P6.4** Propose-change dialog with the live impact panel (delta, resulting compa-ratio, band
  marker movement, peer percentile, annualised cost); note required outside band.
  *Verify:* the panel figures match the API's computed values exactly — no client arithmetic.
- [ ] **P6.5** Changes screen with tabs in the URL and inline approve/reject.
  *Verify:* an approver-less role sees the tab without the actions.

## P7 — Insights

- [ ] **P7.1** `payroll-cost`, `headcount` (FR-6.1) with the full basis envelope.
  *Verify:* totals reconcile against a direct SQL sum over the seed.
- [ ] **P7.2** `out-of-band` (FR-6.2) including cost-to-minimum.
  *Verify:* count matches the seeded anomaly count exactly.
- [ ] **P7.3** `compa-ratio-distribution` (FR-6.3). *Verify:* quartiles match a SQL cross-check.
- [ ] **P7.4** `pay-gap` (FR-6.4) with suppression **inside** the query and a suppressed-cohort
  count. *Verify:* no cohort under 5 appears in any response, at any parameter combination.
- [ ] **P7.5** `increase-cycle` (FR-6.5) and employee `peers` (FR-6.6).
  *Verify:* increase spend for the seeded cycle matches a SQL sum.
- [ ] **P7.6** Overview and Pay analysis screens; charts themed from CSS variables; every card
  carries its basis line and a "View as table" toggle.
  *Verify:* switching themes re-colours every chart; the table equivalent exports.
- [ ] **P7.7** Equity review screen with the suppression notice and separate unadjusted /
  level-adjusted columns. *Verify:* the suppressed count is non-zero against the seed and is shown.

## P8 — Admin, audit, import

- [ ] **P8.1** Users and roles admin; admin-issued reset tokens; last-HR-Admin protection.
  *Verify:* deactivating the last HR Admin is refused.
- [ ] **P8.2** Audit: write aspect, read interceptor, append-only grants, `AuditImmutabilityTest`.
  *Verify:* a pay-list read produces an audit row recording the filter; an update is denied.
- [ ] **P8.3** Audit log screen with filters and export; FX rate admin by month.
  *Verify:* a missing rate month is visible and addable.
- [ ] **P8.4** Employee CSV import with dry-run diff.
  *Verify:* the dry run reports counts and writes nothing.

## P9 — Seed, hardening, acceptance

- [ ] **P9.1** `SeedRunner` and generators per `salary-management-backend.md §9`, including every
  deliberate anomaly. *Verify:* 10,000 employees and ~40k comp records in under 90 seconds; log the
  stage timings.
- [ ] **P9.2** Reproducibility. *Verify:* seed twice from empty; totals, medians, and the anomaly
  counts are identical. Assert it in a test.
- [ ] **P9.3** `DemographicsIsolationTest` across every DTO package outside `analytics`.
  *Verify:* it fails when you add a `gender` field to an employee DTO, and passes when removed.
- [ ] **P9.4** Performance pass against NFR-1…4. *Verify:* record observed p95 for the list, detail,
  and each analytics endpoint. Add indexes only where a measurement justifies it.
- [ ] **P9.5** Accessibility and responsive pass (`salary-management-ui.md §10`, §12 checklist).
  *Verify:* contrast measured in both themes; keyboard-only run through the core flow; 375px.
- [ ] **P9.6** Walk the twelve acceptance criteria in `Technical-Requirements.md §6`.
  *Verify:* each one demonstrated, with the observed result written next to it.
- [ ] **P9.7** README: run instructions, seeded credentials, the seven questions and where each is
  answered. *Verify:* a clean clone reaches a signed-in seeded app using only the README.

---

## Progress log

| | |
|---|---|
| **Last completed** | Backend + frontend for `P4.3` built and verified except a live browser pass (2026-08-21) |
| **Current step** | `P4.3` — **`[~]`, not `[x]`.** Code is done (backend 63/63 tests, frontend build/typecheck/lint clean, API-level curl verification against a seeded throwaway DB). Owed: open `/employees` in a real browser and confirm it actually renders/behaves, plus the §12 checklist's keyboard/375px passes. See the done-note under P4.3 in the plan above for the exact recipe to recreate the throwaway dev DB. |
| **Blockers** | `P0.3` still needs Neon project + `DATABASE_URL` (not required by anything done so far). **Chrome extension was not connected this session** — owed a visual pass over both P2.5 (sign-in) and P4.3 (`/employees`) whenever available. |

_Update both rows on every completed step._

**Phase close (2026-08-21), P2 → P2.5:** backend `./mvnw clean verify` → `Tests run: 58, Failures: 0,
Errors: 0`, `BUILD SUCCESS`. Frontend `npm run lint` / `npm run typecheck` / `npm run build` — all
clean, no errors or warnings.
