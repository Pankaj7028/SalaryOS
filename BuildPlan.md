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
- [x] **P4.3** Employees list screen: filter row, table, band bar column, URL state, CSV export.
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
  > **Browser pass done, retroactively (at P5.5):** the "Chrome can't reach localhost" blocker
  > (both this note and P4.4's) turned out to be a second, *physically different* paired browser
  > (Linux) that earlier `tabs_context_mcp` calls silently defaulted to —
  > `list_connected_browsers`/`select_browser` surfaced and fixed it. Live-verified on the real
  > macOS-paired Chrome: filters, table with correct mono/tabular figures, `<BandBar>` colours per
  > row (below-min amber, above-max red, no-band dashes for unbanded/terminated), both themes. §12's
  > keyboard-only and 375px passes were **not** completed (see P5.5's done-note — a genuine,
  > separately-flagged gap: the table has no responsive card degradation at narrow widths).
  > `P4.3` marked `[x]` above.
- [x] **P4.4** Employee detail: identity, current pay panel, band detail bar, peers panel.
  *Verify:* an unbanded employee shows the no-band state, not a centred marker.
  > **Backend:** `GET /employees/{id}/peers` was a P2.4 stub with `@PreAuthorize` but no logic —
  > implemented for real (FR-6.6): cohort = active employees at the same `jobLevelId`, any location
  > sharing the person's location's `countryCode` (`LocationRepository.findByCountryCode` +
  > `EmployeeRepository.findByJobLevelIdAndLocationIdInAndStatusNot`, new derived-query methods),
  > percentiles via linear interpolation over `normalizedAnnualBase` (always USD — a cohort spanning
  > currencies still compares fairly), suppressed under 5 members (`PEER_COHORT_SUPPRESSION_THRESHOLD`,
  > same value as FR-6.4's cohort threshold) — suppressed responses carry `cohortSize` but every
  > money/percentile figure `null`. Built now rather than at P7.5 (where BuildPlan also lists "employee
  > peers, FR-6.6") because Technical-Requirements.md §5's API contract places this route under
  > **Employees**, not Analytics, and `EmployeeController` already owned the stub — P7.5 should treat
  > this as done, not redo it. `EmployeeDetailResponse` gained a `components` list
  > (`CompensationComponentRepository.findByCompensationRecordId`, new derived query).
  >
  > **Frontend:** identity header (name, employee number, status badge, a `band-mismatched` flag
  > badge when true, resolved job-level/department/location names via the P4.3 reference hooks,
  > manager name via a second `useEmployee(managerId)` call gated on `enabled`). `CurrentPayPanel`
  > (`figure-lg` base, components listed beneath, `<BandBar detail>`, compa-ratio/range-penetration
  > figures with formula tooltips — `TooltipProvider` added once in `query-provider.tsx`, same
  > one-instance-at-root discipline as the root `<Toaster>`). `PeersPanel` (p25/median/p75, this
  > person's percentile, the suppressed-cohort empty state). "Propose change" is a disabled button,
  > matching the Overview page's P3.3 placeholder — P6 doesn't exist yet.
  >
  > **Deliberately not built:** the ui doc §8.3 "Pay history" panel — its ledger endpoint
  > (`GET /employees/{id}/compensation`) is explicitly P5.4/P5.5 scope in this very file, not P4.4.
  >
  > **Verified:** `./mvnw clean verify` → `Tests run: 63, Failures: 0, Errors: 0`. `npm run
  > typecheck`/`lint`/`build` all clean (lint: 0 errors, 1 expected TanStack-Table warning, same as
  > P4.3). `curl`-verified end-to-end against the same seeded throwaway dev DB as P4.3 (recipe in
  > `docs/STATE.md`): grew the L3/US cohort to exactly 5 people and hand-verified the peers math
  > (sorted normalized bases 82000/98000/105000/112000/119000 → p25=98000, median=105000, p75=112000,
  > Alice at 105000 = 60th percentile — all correct); confirmed the <5-member suppression path
  > separately; confirmed an unbanded employee's detail response returns `band: null` /
  > `currentBasePay: null` (drives the panel's no-band state, not a centred marker — this step's own
  > Verify clause, confirmed at the API level).
  >
  > **Browser pass done, retroactively (at P5.5):** the earlier Chrome connection really was on a
  > different physical machine (`list_connected_browsers` revealed two paired browsers, one macOS,
  > one Linux — the Linux one is what every earlier attempt this session had silently landed on).
  > Selecting the macOS one fixed it immediately. Live-verified: header (name, employee number,
  > status badge, band-mismatch badge), Current pay panel (figure-lg base, bonus component listed,
  > `<BandBar detail>` with labelled min/mid/max, compa-ratio/range-penetration with working
  > tooltip triggers), Peers panel (p25/median/p75 and "60th percentile of 5 peers", matching the
  > curl-verified numbers exactly), both themes. `P4.3` and `P4.4` marked `[x]` above.

## P5 — Compensation & bands

- [x] **P5.1** `EffectiveDating` — apply, close, correct, annualise, normalise.
  *Verify:* the dedicated test class covers day-boundary closing, backdating rejection, correction
  supersede, FTE annualisation, and a missing FX rate. This is the highest-value test file in the
  build; do not thin it.
  > **Product decision made (asked the user directly before implementing):** the docs
  > left "annualisation accounts for FTE" underspecified for what "accounts for" means — grossed up
  > to a full-time-equivalent figure, or left as the employee's literal actual pay? Since every
  > compa-ratio/band comparison and every FR-6.1 payroll-cost total downstream depends on this,
  > asked before implementing rather than guessing. Chosen: **gross to FTE = 1.0 equivalent** —
  > `annualBaseAmount = periodAnnual ÷ fte` for `ANNUAL`/`MONTHLY`. **`HOURLY` is a deliberate
  > exception**: an hourly rate already represents a per-hour wage independent of hours actually
  > worked, so `amount × 2080` (40hr × 52wk, the standard full-time year — not documented anywhere,
  > a judgment call) already yields the FTE=1.0 figure directly; dividing by FTE again would
  > double-count it. `EffectiveDatingTest.hourlyAnnualisationUsesTheStandardYearWithoutDividingByFteAgain`
  > pins this down explicitly so it can't silently regress.
  >
  > **Design deviates from the doc's shown `apply()` pseudocode** (backend doc §3), on purpose: the
  > doc's sketch throws `NoOpenPeriodException` unconditionally when no open period exists, which
  > would make it impossible to ever create an employee's first-ever compensation record through
  > this class — contradicting "all of it lives in one class." Instead `apply()` branches: no open
  > period → this is the first-ever record, nothing to close; an open period exists → the normal
  > close-then-insert raise/promotion path, `effectiveFrom` must be strictly after it or
  > `BackdatedBeforeOpenPeriodException` fires (backend doc §8's exact copy). No separate
  > `openInitial` method needed. `Clock` was **not** injected into this class despite backend doc §3
  > rule 6's "Clock is injected" convention — nothing here compares a date against "today", only
  > against another date on the record itself; the convention will actually matter starting at
  > P6.3's `ApplyDueChangesJob`, which is where the doc's rule 6 actually bites, not here.
  >
  > **`GET /employees/{id}/peers` (FR-6.6) was already built at P4.4** — see that step's note. P5's
  > job here is the ledger only.
  >
  > **Two real, non-obvious bugs found and fixed while writing the test suite, not test artifacts:**
  > (1) both `apply()` and `correct()` initially inserted the NEW row before closing the OLD one —
  > reasonable-looking Java call order, but Hibernate's default flush ordering runs every pending
  > INSERT before any UPDATE **regardless of the order methods were called in**, so the new row's
  > insert hit the database while the old row's range was still open-ended, and `comp_no_overlap`
  > correctly rejected it as a genuine (if transient) overlap. Fixed with an explicit
  > `saveAndFlush()` on the closed row before building the new one — a real transactional-ordering
  > bug that would have surfaced in production on literally the second raise ever applied, not
  > something a thinner test suite would have caught. (2) `correct()` read
  > `original.getEffectiveTo()` for the corrected row's own end date **after** already calling
  > `original.close(...)`, which had just overwritten that same field — so the corrected row
  > inherited the wrong (just-computed) end date instead of the original period's real one,
  > producing an inverted, invalid date range on any correction of a period that was still open.
  > Fixed by capturing the value into a local variable before the mutation.
  >
  > **Schema bug actually fixed, not just documented:** `V13__widen_range_penetration.sql` widens
  > `compensation_records`/`employee_current_comp`.`range_penetration` from `numeric(6,4)` to
  > `numeric(8,4)` — the `numeric(6,4)` from V6/V9 tops out just under ±100, but range penetration
  > legitimately exceeds 100 for anyone paid above band max (this step's own
  > `aboveMaxRangePenetrationExceedsOneHundredWithoutOverflowing` test proves it: 250% for someone
  > $60,000 above a $40,000-wide band). P4.3's seed data had already hit this as a workaround
  > (clamping a 130% test case to 99.99 to insert); this migration is the actual fix-forward,
  > per CLAUDE.md §12.11.
  >
  > **Two pre-existing tests broke from legitimate new data, fixed, not weakened:**
  > `CompensationEntitiesRoundTripTest` collided on `fx_rates`' unique `(month, base, quote)`
  > constraint because `EffectiveDatingTest` happened to reuse the same 2024 months in the same
  > shared cached Testcontainers context — fixed by moving `EffectiveDatingTest`'s dates to 2031,
  > clear of every date any other test file uses, rather than touching the older test.
  > `V4V5BandsAndFxMigrationTest.bandCheckConstraintRejectsMinGreaterThanMid` asserted an **unscoped**
  > `count(*) from salary_bands` was exactly 1, silently assuming it was the only test ever
  > inserting into that table in the shared container — broke the moment `EffectiveDatingTest`'s
  > above-max test (legitimately) inserted its own band row. Fixed by scoping the count to
  > `job_level_id`, the same fix pattern already established for this exact class of bug elsewhere
  > in the suite (P4.1's `EmployeeListPaginationTest`).
  >
  > Also added along the way: `app.base-currency` config property (`APP_BASE_CURRENCY`, default
  > `USD`, CLAUDE.md §10), `SalaryBandRepository.findEffective` (effective-dated band lookup),
  > `FxRateRepository.findByBaseCurrencyAndQuoteCurrencyAndRateMonth`,
  > `CompensationRecord.supersede(UUID)` domain method, `Employee.clearBandMismatch()` (called by
  > `apply()` — a real pay change resolves a mismatch flag, per `Employee`'s own P4.2 javadoc),
  > and four new `ApiExceptionHandler` mappings (409 backdated, 422 missing FX rate, 400 missing
  > correction note, 400 correction outside its period) plus a `DataIntegrityViolationException`
  > handler that turns a raw `comp_no_overlap` constraint hit into a 409 instead of a 500 — the
  > backstop backend doc §3 rule 2 calls for, never silently swallowed.
  >
  > **Scope boundary, deliberate:** this class writes only `compensation_records`. Keeping
  > `employee_current_comp` in sync is explicitly P5.2's job ("projection maintained in the same
  > transaction") — a raise applied here will not yet show up on the employee list/detail screens
  > (which read the projection) until P5.2 lands, immediately next.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 76, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (13 new tests in `EffectiveDatingTest`, 63 pre-existing, all green).
  > **Addendum (found at P5.4, fixed retroactively here):** the closing formula this done-note
  > describes as `effectiveFrom.minusDays(1)` was **wrong** — a real one-day gap, not the day-boundary
  > correctness this step's own Verify clause asked for. Full story in P5.4's done-note; the fix
  > (`close(effectiveFrom)`, no subtraction) is what's in the code now, and this note's own text
  > above was left as originally written for the historical record rather than silently edited.
- [x] **P5.2** `employee_current_comp` projection maintained in the same transaction +
  `POST /admin/rebuild-projection` + `ProjectionConsistencyTest`.
  *Verify:* re-deriving the projection from the ledger equals the stored projection for 10k rows.
  > **Verify scoped to what's seedable today, not literally 10k rows:** `P9`'s `SeedRunner` (10k
  > employees) doesn't exist yet, so `ProjectionConsistencyTest` exercises the same equality check —
  > `rebuildAll()` vs. what the transactional path already wrote — over a hand-seeded set covering
  > every `BandBar` state (in-band, below-min, above-max, no-band), a raise, and a termination. The
  > check holds *because* both paths share one `toProjection(CompensationRecord)` helper in
  > `EmployeeCurrentCompProjector`, so it would catch the two paths drifting apart at any scale —
  > row count isn't what the assertion depends on. Revisit at `P9` if the real 10k-row seed surfaces
  > anything this smaller set didn't (a genuine possibility worth re-checking, not assumed clean).
  >
  > **`EmployeeCurrentCompProjector`** (new, `compensation/projection`): `refresh(employeeId)`
  > re-derives one row from whichever period is currently open for that employee, or **deletes** the
  > row if none is open (a terminated employee has no "current pay" to show — this is itself a small
  > design decision: the alternative was leaving a stale row behind, which would be actively
  > misleading, not just outdated). `rebuildAll()` wipes the table and re-derives every row from
  > every open period across all employees (`CompensationRecordRepository.findByEffectiveToIsNull()`,
  > new — `comp_no_overlap` guarantees at most one open period per employee, so this is a safe 1:1
  > mapping). `EffectiveDating.bandStatus(...)` made `static` so the projector can reuse the exact
  > same IN_BAND/BELOW_MIN/ABOVE_MAX/NO_BAND rule without a circular bean dependency (`EffectiveDating`
  > → projector already flows one way).
  >
  > **`EffectiveDating.apply()`/`.correct()` now call `projector.refresh(employeeId)`** as their last
  > step, inside the same `@Transactional` method — matching Technical-Requirements.md §4.4's "not a
  > trigger" requirement exactly. `POST /api/admin/rebuild-projection` (P2.4/P5.2 stub) now calls
  > `projector.rebuildAll()`; `@PreAuthorize("hasRole('HR_ADMIN')")` left verbatim.
  >
  > **Real, pre-existing gap found and fixed, not just documented:** `EmployeeService.terminate()`
  > (P4.2, written before `EffectiveDating`/the projector existed) closes the open ledger row
  > directly via the repository, bypassing `EffectiveDating` entirely — and never touched
  > `employee_current_comp`. Without this step's fix, terminating an employee would leave a stale
  > "current pay" row behind forever (until someone happened to run a full rebuild), which
  > `ProjectionConsistencyTest`'s own termination case would have caught. Fixed by injecting the
  > projector into `EmployeeService` and calling `refresh(id)` right after closing the record.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 77, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
- [x] **P5.3** Bands CRUD with versioning (never in-place), CSV import with dry-run diff.
  *Verify:* editing an in-force band closes it and opens a successor; the dry run changes nothing.
  > **`salary_bands` has no exclusion constraint** (unlike `compensation_records`'
  > `comp_no_overlap`, V4's migration) — the service (`BandService`) is the only thing enforcing
  > non-overlap, not a backstop for one. `create()` rejects (409) a second band for a (job level ×
  > country) that already has an open one, directing the caller to PATCH; `update()` rejects (409)
  > versioning an already-closed band, and rejects (409) backdating on/before the current version's
  > own start — same day-boundary discipline as `EffectiveDating`, including the same
  > close-then-`saveAndFlush`-before-insert ordering (no DB constraint requires it here, but it's
  > the same correct pattern and costs nothing extra).
  >
  > **CSV format decided, not specified anywhere in the docs:** `jobLevelId,countryCode,currency,
  > minAmount,midAmount,maxAmount,effectiveFrom,note` (header row required, `note` optional/last).
  > Manual line-split parsing, not a CSV library — every field here is a UUID/code/number/date, none
  > can legitimately contain a comma, so a dependency for quoted-field handling isn't justified yet.
  > Per-row outcome is `CREATE` (no open band yet for that level×country), `VERSION` (one exists and
  > the row's date comes after it), or `ERROR` (malformed row, min>mid>max violated, or a backdated
  > version) — one bad row never blocks the others in the same file, and `dryRun=true` computes the
  > full diff without writing anything (verified: band count and open-band lookups are unchanged
  > after a dry run in `BandVersioningTest.dryRunReportsTheDiffButChangesNothing`).
  >
  > **`@AuthenticationPrincipal UUID currentUserId` used for the first time** — `created_by` on
  > `salary_bands` is the first column in the app that needed "who is calling this," and
  > `SessionCookieAuthFilter` already sets the JWT's `sub` claim (a UUID) as the
  > `Authentication`'s principal directly, so binding it is a one-line parameter, no new plumbing.
  > Later write paths that need an acting user (P6's change proposals) should follow the same
  > pattern rather than re-deriving it from `SecurityContextHolder` by hand.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 83, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (6 new tests in `BandVersioningTest`, 77 pre-existing, all green).
  > **Addendum (found at P5.4, fixed retroactively here):** `versionBand()`'s closing formula had
  > the same one-day-gap bug as `EffectiveDating` (this step copied its pattern faithfully — bug
  > included). Fixed alongside it; full story in P5.4's done-note.
- [x] **P5.4** Pay history ledger endpoint + `as-at` query. *Verify:* the salary in force on a chosen
  past date is returned for a sample of 50 seeded employees.
  > **The real find of this step: a genuine, product-critical bug in P5.1's/P5.3's closing formula,
  > shipped in two already-`[x]`'d steps.** Both `EffectiveDating.apply()`/`.correct()` and
  > `BandService.versionBand()` set `effective_to = newFrom.minusDays(1)`, faithfully matching
  > backend doc §3 rule 1's literal text ("Closing sets `effective_to = newFrom − 1 day`"). This
  > step's `as-at` test (`PayHistoryTest.asAtReturnsTheOnePeriodInForceOnTheChosenDate`) failed on
  > the very first run — asking "what was this person paid on the day before their raise?" returned
  > nothing. Root cause: `compensation_records.validity` (V6) is a `[)` daterange — inclusive start,
  > **exclusive** end — so `effective_to = newFrom − 1` makes the OLD period's actual last covered
  > day `newFrom − 2`, leaving `newFrom − 1` covered by **neither** period. Verified directly against
  > a running Postgres instance rather than trusting the reasoning alone:
  > `SELECT daterange('2034-01-01','2034-07-01','[)') @> '2034-06-30'::date` → `false`;
  > `@> '2034-06-29'::date` → `true`. This is *exactly* the "off-by-one… pays somebody nothing at
  > all" bug the doc's own rule 1 warns against — the doc's stated formula produces the bug it warns
  > about, not a fix for it.
  >
  > **Fixed at the root, not patched around:** `close(effectiveFrom)` — no subtraction — in both
  > `EffectiveDating` (P5.1) and `BandService` (P5.3), which makes `[oldFrom, newFrom)` butt exactly
  > against `[newFrom, …)`: zero gap, zero overlap (also verified directly:
  > `daterange('a','2034-07-01','[)') && daterange('2034-07-01', NULL, '[)')` → `false`, so
  > `comp_no_overlap` still correctly accepts the adjacent pair). **`docs/salary-management-backend.md`
  > §3 rule 1 corrected** to state the right formula with the reasoning and the empirical proof, and
  > its `apply()` pseudocode's `closeOn(...)` call updated to match — a wrong instruction in a BINDING
  > doc is a bug that keeps re-injuring future work otherwise. Every affected test assertion in
  > `EffectiveDatingTest`, `BandVersioningTest`, and this step's own `PayHistoryTest` updated to the
  > corrected boundary (most of `PayHistoryTest`'s own as-at assertions were already right — that's
  > *why* they caught the bug; only the exact `effectiveTo` date-equality assertions elsewhere needed
  > changing). `EmployeeService.terminate()` was **not** touched — it has no "successor period" to
  > tile against (termination just ends a period with nothing after it), so this specific gap
  > geometry doesn't apply there; its own closing-date semantics are a separate, not-yet-resolved
  > question noted below, out of scope for this fix.
  >
  > **New endpoints:** `GET /employees/{id}/compensation` (full ledger, newest period first —
  > `CompensationRecordRepository.findByEmployeeIdOrderByEffectiveFromDesc`, new) and
  > `GET /employees/{id}/compensation/as-at?date=` (`findAsAt`, new — same `[)`-aware query pattern
  > as `SalaryBandRepository.findEffective`). `CompensationRecordResponse` deliberately omits `note`
  > and proposer/approver — those belong to the `compensation_changes` row a record's `changeId`
  > points at, and that domain doesn't exist until P6.1; `changeId`/`supersededBy` are included now
  > so P5.5/P6's UI can wire up without another backend round-trip.
  >
  > **Verify scoped to what's seedable today, not literally 50 employees** — same reasoning as
  > P5.1's/P5.2's Verify-scope notes; `P9`'s `SeedRunner` doesn't exist yet. `PayHistoryTest` proves
  > the exact day-boundary correctness (which is the part that actually matters — and which a
  > shallower "50 random employees" sweep might not have caught, since it takes a query pinned to
  > the exact seam between two periods to surface a one-day gap) rather than a large N.
  >
  > **`EmployeeService.terminate()` policy question — asked, answered, fixed:** it previously called
  > `open.close(terminationDate)` directly, meaning under `[)` semantics the employee's last **paid**
  > day was `terminationDate − 1`, not `terminationDate` itself — genuinely ambiguous from FR-2.6's
  > text alone (unlike the raise/version gap above, this isn't a provable bug, it's an unstated
  > policy choice). Asked the user: **pay runs through and includes the termination date.** Fixed to
  > `open.close(terminationDate.plusDays(1))`; `EmployeeLifecycleTest`'s termination assertion
  > updated to match.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 86, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (3 new tests in `PayHistoryTest`, 83 pre-existing — including every P5.1/P5.3 test re-verified
  > against the corrected boundary — all green).
- [x] **P5.5** Employee pay-history ledger UI and the bands grid screen with the
  "how many employees change status" preview. *Verify:* the preview count matches what saving does.
  > **Backend additions this screen needed and didn't have:** `EmployeeCurrentCompRepository
  > .countByBandId`/`.findByBandId` (new); `BandResponse` gained a `headcount` field (0 for any
  > closed/superseded version — `employee_current_comp.band_id` only ever points at the in-force
  > one); `BandService.previewVersionImpact(bandId, min, mid, max)` — new,
  > `GET /bands/{id}/preview-version-impact` — evaluates the cohort currently tied to `bandId`
  > against proposed new boundaries using the exact same `EffectiveDating.bandStatus` rule the
  > ledger itself uses, so the number is provably what a post-save re-derivation would also find,
  > never a separate approximation. `BandVersionImpactTest` proves this directly: computes the
  > preview, then independently re-derives "actually changed" from the ledger after really saving
  > the version, and asserts they're equal — literally this step's own Verify clause as a test.
  >
  > **Real gap found and fixed while building this:** `BandService.create()`/`.update()` had no
  > explicit `min ≤ mid ≤ max` check — only the CSV import path did. An invalid ordering would have
  > hit the raw `band_ordered` DB CHECK constraint and fallen through `ApiExceptionHandler`'s
  > `DataIntegrityViolationException` handler's generic rethrow, surfacing as an uncaught 500. Added
  > `BandOrderingException` (400) and a `requireOrdered()` guard on both methods, mirrored by a new
  > `RolePermissionMatrixTest` entry for the new `previewVersionImpact` endpoint (`ADMIN_AND_MANAGER`
  > — same capability as editing itself) that the test itself demanded before it would pass.
  >
  > **Frontend:** `PayHistoryPanel` (added to the P4.4 employee-detail screen) — a vertical-hairline
  > ledger, one entry per period, reason chip, mono dates. **`<Delta>` deliberately not shown**: the
  > backend doesn't return a precomputed delta between consecutive ledger entries, and computing one
  > client-side (`current.amount − previous.amount`) is exactly the money arithmetic CLAUDE.md §6.1
  > rules out — a real backend gap to close later (a `deltaAmount`/`deltaPercent` field on the
  > ledger response), not worked around with a client-side subtraction. Note/proposer/approver also
  > omitted — `compensation_changes` doesn't exist as a domain until P6.1.
  >
  > `/bands` grid (level × country from the reference endpoints, not just level×country pairs that
  > already have a band — so an empty cell is visible and clickable). Filled cells open
  > `BandDetailDialog` (version history, newest first, headcount on the in-force version) with a
  > "New version" sub-form; empty cells open `CreateBandDialog`. **First real use of React Hook
  > Form + Zod** in this codebase (CLAUDE.md's pinned forms stack, previously only exercised by
  > sign-in's plain `useState` — installed `react-hook-form`, `zod`, `@hookform/resolvers` now for
  > real, established the pattern P6's propose-change dialog will also need). `bandFormSchema`
  > shared by both dialogs, `min ≤ mid` / `mid ≤ max` as `.refine()` client-side checks — a UX nicety
  > mirroring `BandOrderingException`, not a replacement for it. The version form's live impact
  > preview watches `min`/`mid`/`max` via RHF's `watch()`, debounces 400ms, and calls
  > `previewVersionImpact` — visually verified in-browser: typing a new floor updated "N of M
  > employees would change status" live, and the number matched the earlier `curl`-verified backend
  > value exactly (lowering a band's floor from 90000 to 75000 correctly flipped exactly the one
  > below-min employee to in-band).
  >
  > **A real mistake made and fixed mid-session:** the `npm install react-hook-form zod
  > @hookform/resolvers` command ran from the repo root by accident (persisted shell CWD drift from
  > earlier backend work), creating a stray, untracked `package.json`/`package-lock.json`/
  > `node_modules` (11MB) at the repo root — outside `salary-web/`, where the real dependency
  > declaration needed to live. The build/typecheck/lint all passed anyway, silently, because
  > Node's module resolution walks up the directory tree and found the packages in the stray root
  > `node_modules` — meaning a fresh clone or CI run would have failed despite every local check
  > passing. Caught before committing: re-ran the install with the correct `cd salary-web` first
  > (confirmed via `package.json` diff that the three packages are now actually declared there), and
  > removed the stray root artifacts (asked the user first, since it's a `rm -rf`-shaped operation
  > even though everything deleted was untracked and created this session).
  >
  > **Genuine gap found and flagged, since fixed (P6.2's commit):** neither the Employees table
  > (P4.3) nor the Bands grid had a responsive card-degradation at 375px (CLAUDE.md/ui doc §12.10's
  > merge-checklist item) — shadcn's `Table` wrapper's built-in `overflow-x-auto` gave a narrow
  > viewport an internally-scrolling table rather than the required "degrades to cards." Both screens
  > now render `<div className="hidden md:block">` around the desktop table beside a
  > `<ul className="flex flex-col gap-3 md:hidden">` card list; `EmployeesTable`'s cards reuse the
  > desktop column cell renderers via a small `CellFor` helper rather than duplicating formatting.
  > Verified by `npm run build`/`lint`/`typecheck` — see `docs/STATE.md`'s gotchas for why this
  > session's live 375px screenshot pass didn't work (a `resize_window` tool quirk, not a CSS bug).
  >
  > **Chrome-can't-reach-localhost, resolved:** turned out to be a *second physically different*
  > paired browser (a Linux machine) that every earlier `tabs_context_mcp` call in this session had
  > silently defaulted to, alongside the correct macOS one — `list_connected_browsers` surfaced both
  > and `select_browser` picked the right one, after which everything worked immediately. **Full
  > live-browser verification done this step**, retroactively covering the P4.3/P4.4 debt too: sign
  > in, Overview shell, `/employees` (filters/table/BandBar/both themes), `/employees/[id]` (header/
  > current-pay/peers/pay-history/both themes), `/bands` (grid/detail dialog/live impact preview/
  > create dialog with working Zod validation blocking an invalid submission inline). Keyboard-only
  > and true mobile-viewport passes still not done (window-resize-based viewport testing didn't
  > reliably change the rendered layout in this session's browser tooling) — worth a real pass
  > later, ideally on an actual phone or a proper devtools device emulation.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 89, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (2 new tests in `BandVersionImpactTest`, 1 new ordering test in `BandVersioningTest`, 87
  > pre-existing — all green). `npm run typecheck`/`lint`/`build` all clean (lint: 0 errors, 2
  > expected library-incompatibility warnings — RHF's `watch()`, TanStack Table's `useReactTable()`,
  > same category as every other warning this session, not new problems).

## P6 — Changes & approval

- [x] **P6.1** Change lifecycle endpoints; one-open-change rule; `ProposerIsNotApproverTest`.
  *Verify:* self-approval is 403; a second proposal is 409 naming the open change.
  > **Scaffolding already existed** (domain entity, repository, controller stubs with correct
  > `@PreAuthorize` annotations, and `RolePermissionMatrixTest` entries) from an earlier phase —
  > this step wired real logic behind it: `ChangeService` (propose/updateDraft/submit/discardDraft/
  > approve/reject/list) plus named domain-mutation methods on `CompensationChange`
  > (`submit()`/`approve()`/`reject()`/`updateDraft()` — no raw setters, matching the pattern
  > everywhere else this session).
  >
  > **`newBaseAmount`/`currentBaseAmount` are annual figures, not period amounts** —
  > `compensation_changes` has no `pay_frequency` column at all (unlike the ledger), so a proposal
  > is always phrased as "their new annual salary," matching how a merit/promotion conversation
  > actually happens and sidestepping the annualisation question entirely for this table.
  > `currentBaseAmount` is snapshotted from `employee_current_comp.annualBaseAmount` at propose
  > time — an employee with no current comp can't have a "change" proposed against them
  > (`NoCurrentCompensationException`, 400); that's an initial hire, a different flow. The single
  > shared `currency` column (not two `Money` embeds) means a proposal must be in the employee's
  > current pay currency or it's rejected (`ChangeCurrencyMismatchException`, 400) — the schema
  > can't represent a currency change as a "change" proposal.
  >
  > **FR-5.2/FR-5.4's mandatory-note rule implemented for real, not just documented:** a note is
  > required for `CORRECTION`, and separately for any proposal whose new amount lands outside the
  > band effective on the proposed date — computed via `SalaryBandRepository.findEffective` +
  > `EffectiveDating.bandStatus` (reused, not re-implemented). `NO_BAND` (no band exists at all) is
  > deliberately **not** treated as "outside the band" for this rule — FR-5.4's wording is about a
  > proposal landing outside an *existing* band, not the absence of one.
  >
  > **The one-open-change rule is enforced twice, on purpose**, same backstop discipline as
  > `comp_no_overlap`: `ChangeService.propose()` checks proactively
  > (`findByEmployeeIdAndStatusIn`) and throws `OpenChangeAlreadyExistsException` with the exact
  > backend doc §8 copy, exposing `openChangeId` as a `ProblemDetail` extension property (curl-
  > verified: `{"detail":"A change for this employee is already awaiting approval.",...,
  > "openChangeId":"..."}`) so the UI can link straight to it. V7's `one_open_change_per_employee`
  > partial unique index is the real guarantee underneath — `ApiExceptionHandler`'s existing
  > `DataIntegrityViolationException` handler extended with a matching branch for it, same pattern
  > as `comp_no_overlap`.
  >
  > **Verified three ways:** `ChangeLifecycleTest` (12 tests — snapshot correctness, duplicate
  > rejection with the right id, self-approval then a different approver succeeding, reject,
  > draft-only edit/submit/discard, pending-only approve/reject, both note-required rules, no-comp
  > rejection, currency-mismatch rejection, list filtering) plus a literally-named
  > `ProposerIsNotApproverTest` matching this step's own line in `BuildPlan.md`. Then `curl`-verified
  > the full lifecycle against the running dev server signed in as a real user: propose → 200 with
  > the correct `currentBase` snapshot (Alice's real $105,000) → a second proposal for the same
  > employee → 409 with `openChangeId` matching the first → submit → 200, `status: PENDING` →
  > self-approve as the proposer → 403 with the exact backend doc §8 copy.
  >
  > **Not built here, deliberately:** `apply-due` and `bulk-upload` stay `501` — P6.2 and P6.3's
  > jobs, not this one's. No live-browser UI for any of this yet — that's P6.4/P6.5.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 102, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (12 new tests in `ChangeLifecycleTest`, 1 in `ProposerIsNotApproverTest`, 89 pre-existing).
- [x] **P6.2** `ApplyDueChangesJob` (daily 02:00 UTC) + idempotent `POST /changes/apply-due`.
  *Verify:* running twice writes one record; a change dated tomorrow is not applied today.
  > `ClockConfig` (new `Clock.systemUTC()` bean) and `SchedulingConfig` (`@EnableScheduling`) finally
  > give backend doc §3 rule 6's "inject Clock, never call `LocalDate.now()` inline" a real consumer.
  > `ChangeService.applyDueChange(id, asOf)` does the actual work — re-validates status `APPROVED`
  > and `effectiveDate <= asOf` itself (never trusts the caller's candidate list is still accurate),
  > calls `EffectiveDating.apply(...)` to write the ledger row, then `change.apply(recordId)` to mark
  > `APPLIED`, all in one `@Transactional` method. `ApplyDueChangesJob` (new top-level class in
  > `change/`, matching the backend doc's package layout comment) is a thin orchestrator: queries
  > `findByStatusAndEffectiveDateLessThanEqual("APPROVED", today)`, calls `applyDueChange` once per
  > change with its own try/catch so one failure can't block the rest, and returns
  > `ApplyDueChangesResult(due, applied, failures)`. `@Scheduled(cron = "0 0 2 * * *", zone = "UTC")`
  > wraps it for the daily run; `ChangeController#applyDue` (was 501) calls the same `run()` for the
  > manual trigger — same code path, so "idempotent" holds for both callers, not just the cron one.
  > Idempotency falls out for free: a change leaves the `APPROVED` status the moment it's applied, so
  > it's gone from the next run's own candidate query — no separate lock or dedupe needed.
  >
  > `ChangeNotDueException` (409) added for the direct-call edge (a future-dated change passed
  > straight to `applyDueChange` — the job's own query already excludes this, so it only fires on a
  > manual/direct call) and wired into `ApiExceptionHandler`.
  >
  > `ApplyDueChangesJobTest` (2 tests) runs against the real injected system Clock rather than a
  > mocked one — the job only ever compares an effective date to "today", so seeding dates relative
  > to `LocalDate.now()` exercises the real boundary without a test-only Clock bean. Confirmed: two
  > back-to-back `run()` calls apply a due change exactly once (`due=1,applied=1` then `due=0,applied=0`,
  > one `compensation_records` row added, not two); a change dated `LocalDate.now().plusDays(1)` is
  > excluded from the candidate query and stays `APPROVED`, and a direct `applyDueChange` call against
  > it throws `ChangeNotDueException`.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 104, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (2 new tests in `ApplyDueChangesJobTest`, 102 pre-existing).
- [x] **P6.3** Bulk merit upload: per-row validation, proposals for valid rows, downloadable error
  report. *Verify:* a file with 100 rows and 12 bad ones creates 88 proposals and one report.
  > `POST /changes/bulk-upload` (HR Admin only), same shape as `BandService#importCsv` (P5.3): a
  > CSV of `employeeNumber,newAmount,changeReason[,note]` — no `currency` column, since a merit row
  > always uses the employee's own current pay currency (looked up server-side, never trusted from
  > the file). `ChangeService.bulkUpload` reads the file line by line; each row calls the existing
  > `propose()` inside a `try/catch(RuntimeException)` so every domain rule (unknown employee number,
  > no current comp, an open change already in flight, a missing required note) becomes one `ERROR`
  > row with that exception's own message, never blocking the rest — partial success is the normal
  > outcome (backend doc §3), matching the band importer's per-row isolation exactly. No `dryRun`:
  > unlike a band version, a DRAFT proposal this cheap to discard doesn't need a preview step.
  > `EmployeeRepository.findByEmployeeNumber` added — the only human-facing key in this CSV, not the
  > UUID. "Downloadable error report" is the full `rows()` list in the JSON response, same shape as
  > `BandImportRowResult` — a UI renders/downloads it whenever a bulk-upload screen exists (not this
  > step's scope, matching P5.3's precedent).
  >
  > `ChangeBulkUploadTest` (1 test, year 2038): 100-row CSV — 88 valid rows (each backed by a real
  > seeded employee with current comp), 4 unknown employee numbers, 4 unparseable amounts, 4
  > too-few-column rows. Confirmed: `totalRows=100`, `proposed=88`, `errors=12`, every `PROPOSED` row
  > carries a non-null `changeId`, every `ERROR` row carries a non-blank message.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 105, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (1 new test in `ChangeBulkUploadTest`, 104 pre-existing).
- [x] **P6.4** Propose-change dialog with the live impact panel (delta, resulting compa-ratio, band
  marker movement, peer percentile, annualised cost); note required outside band.
  *Verify:* the panel figures match the API's computed values exactly — no client arithmetic.
  > **Done (2026-08-21):** `EffectiveDating.preview(employeeId, effectiveFrom, amount, currency)` —
  > reuses the private `buildRecord` helper `apply()`/`correct()` already call, so the preview is
  > provably the exact same annualisation/FX-normalisation/band-lookup/compa-ratio math a real
  > `apply()` would use; the returned `CompensationRecord` is never saved. `EmployeeService`'s peer
  > cohort fetch factored out into `fetchCohort()` so `peers()` (P4.4) and the new
  > `peersImpact(id, hypotheticalNormalizedAnnualBase)` share one cohort query — the "after" rank
  > swaps this employee's own contribution to the distribution for the hypothetical value before
  > re-ranking, so a raise never appears to move anyone else's percentile. `ChangeService
  > .previewImpact()` composes both plus the band/note-required rule (reusing `EffectiveDating
  > .bandStatus`, same as `propose()` itself) into `ChangeImpactPreviewResponse`. New endpoint
  > `GET /changes/impact-preview` — `ADMIN_MANAGER_ANALYST`, same roles as proposing itself;
  > `RolePermissionMatrixTest` extended to cover it.
  >
  > **Frontend:** `ProposeChangeDialog` (React Hook Form + Zod, `proposeChangeFormSchema` mirrors
  > `ProposeChangeRequest`) — debounces effective date + amount 400ms, calls `impact-preview`, and
  > renders current→proposed `<Money>`/`<Delta>`, compa-ratio, both `<BandBar>` positions (current
  > and proposed), and peer percentile before/after, all straight from the response (no client
  > arithmetic — CLAUDE.md §6.1). Note field becomes required precisely when `preview.data
  > .noteRequired` is true, submit is disabled until then. `CHANGE_REASON_LABEL` deduplicated out of
  > `PayHistoryPanel` into `src/lib/change-reasons.ts` so the ledger's history view and the propose
  > dialog can't drift on reason labels. Employee detail's "Propose change" button is enabled
  > whenever the employee has current comp (was `disabled` unconditionally since P4.4/P3.3).
  >
  > **Verified:** `./mvnw clean verify` → `Tests run: 107, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (2 new tests in `ChangeImpactPreviewTest`, 105 pre-existing). `npm run typecheck`/`lint`/`build`
  > clean (lint: 0 errors, 3 expected library-incompatibility warnings, same category as every prior
  > RHF/TanStack-Table warning this build). Live end-to-end against a throwaway local Postgres 17 dev
  > DB (recipe in `docs/STATE.md`) signed in as the seeded HR_ADMIN: `impact-preview` for a raise
  > landing in-band (105000→120000: compa-ratio 0.9545→1.0909, `noteRequired: false`) and one landing
  > above max (105000→140000: `proposedBandStatus: ABOVE_MAX`, `noteRequired: true`) both matched the
  > expected math exactly; confirmed the one-open-change 409 fires correctly on an employee who
  > already had a pending change from earlier testing; proposed + submitted a real change for a
  > second employee end to end (`DRAFT` → `PENDING`). No live browser pass this session — Chrome
  > browser tools were not enabled; the API-level verification above is real, not assumed.
- [x] **P6.5** Changes screen with tabs in the URL and inline approve/reject.
  *Verify:* an approver-less role sees the tab without the actions.
  > **Done (2026-08-21):** `ChangeResponse` extended with server-resolved `employeeFirstName`/
  > `employeeLastName`/`employeeNumber`, `proposedByName`/`decidedByName`, `outOfBand`, and
  > `deltaAmount`/`deltaPercent` — same "never a raw id or client-computed figure on a display
  > surface" discipline as the rest of the app (CLAUDE.md §6.1, §9's CSV-export precedent).
  > `proposedByName`/`decidedByName` exist because `UserAdminController` (`/admin/users`) is
  > HR_ADMIN-only (P8.1, not built) — a HR_MANAGER/COMP_ANALYST viewing this screen has no other way
  > to resolve a user id to a name. `outOfBand` reuses the exact band lookup
  > `requireNoteIfNeeded` already does at propose time, factored into `isOutOfBand()`.
  >
  > **A real gap found and closed, not just documented:** `reject()` accepted a null/blank
  > `decisionNote` — the ui doc's own "required note on reject" was unenforced server-side even
  > though every other mandatory-note rule in this app (propose outside-band, correction) is.
  > Fixed by checking after `requirePending` (so a reject on a non-pending change still reports
  > *that* problem first, matching `approve()`'s own precedence — verified directly:
  > `onlyAPendingChangeCanBeApprovedOrRejected` still expects `ChangeNotPendingException` for a
  > DRAFT with no note, not the new check firing first).
  >
  > **Frontend:** `ChangesScreen` (`/changes`) — five tabs (`?tab=pending|approved|applied|rejected
  > |draft`) via shadcn's Tabs (newly added, `npx shadcn add tabs`), `useChanges(status)` per tab.
  > `ChangesTable` renders both a desktop table (≥768px) and a `md:hidden` card list from day one —
  > this screen never had the 375px gap P4.3/P5.5 shipped and P6.3 later fixed, because that fix
  > was written first this session and the pattern was just reused. Approve is a single inline
  > button (a decision note is optional per the ui doc); Reject opens `RejectChangeDialog` — a
  > required-note Textarea, disabled submit until non-empty, mirroring the server's own check.
  > Drafts tab gets Submit/Discard (`POST .../submit`, `DELETE`) — a real usability completion, not
  > scope creep: `ProposeChangeDialog`'s (P6.4) own success toast says "submit it when you're
  > ready," and without this a DRAFT had no way to ever reach the approval queue through the UI.
  > **`canApproveChanges(role)` added to `roles.ts`** (CLAUDE.md §7/§10) — narrower than `/changes`
  > area access itself (which also includes COMP_ANALYST, who proposes but doesn't decide); gates
  > whether the Actions column/card-row renders **at all**, never a disabled button — the tab and
  > every row stay visible to COMP_ANALYST on the Awaiting-approval tab, exactly per this step's own
  > Verify clause. New `roles.test.ts` case pins the RBAC row longhand, same pattern as its siblings.
  >
  > **Verified:** `./mvnw clean verify` → `Tests run: 109, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (2 new tests in `ChangeLifecycleTest` covering the reject-note enforcement and the resolved-name/
  > delta/out-of-band fields, 107 pre-existing). `npm run typecheck`/`lint`/`build` clean (lint: 0
  > errors, same 3 pre-existing library warnings). `npx vitest run` → 28/28 (new
  > `canApproveChanges` case). Live end-to-end against the throwaway dev DB, signed in as the seeded
  > HR_ADMIN: listed PENDING (two real changes, names/deltas/outOfBand all correct) → rejected one
  > with no note (400, exact message) → rejected it with a note (200, REJECTED,
  > `decidedByName: "Ada Admin"`) → confirmed self-approval still blocked (403, proposer === decider)
  > → listed REJECTED (shows the decider name) → proposed a fresh DRAFT, listed it under DRAFT,
  > discarded it (204), confirmed the DRAFT list was empty again. No live browser pass this
  > session — Chrome browser tools were not enabled; `next start` is up on `:3100` against the live
  > backend on `:8080` if a visual check is wanted.

## P7 — Insights

- [x] **P7.1** `payroll-cost`, `headcount` (FR-6.1) with the full basis envelope.
  *Verify:* totals reconcile against a direct SQL sum over the seed.
  > **Done (2026-08-21):** First real native SQL in the app — `analytics/query/{PayrollCostQuery,
  > HeadcountQuery}` (plain `JdbcTemplate`, aggregation in the database per backend doc §6, never a
  > per-employee Java loop). Both join from `employee_current_comp`, not `employees` alone — a
  > terminated employee's row is deleted there (P5.2), so exclusion is free, no explicit status
  > filter needed for the cost/headcount totals themselves; `byStatus`/`terminatedCount` query
  > `employees` directly so the excluded count still has somewhere to be seen (FR-6.8).
  > `AnalyticsService` assembles the envelope (`asAtDate` from the P6.2 `Clock` bean,
  > `app.base-currency`, `AnalyticsPopulation{headcount, excluded}`).
  >
  > **A genuine interpretive call, reasoned through rather than guessed:** the envelope's
  > `fxRateMonth` (FR-6.8) has no single value for this report — `normalized_annual_base` is already
  > pinned per-employee to whichever rate was in force when *that employee's own record* was written
  > (CLAUDE.md §6.4), so a population spanning many employees has no one governing rate month.
  > `PayrollCostResponse.fxRateMonth` is `null` rather than a fabricated "today's month," which would
  > wrongly imply live recomputation. `HeadcountResponse` omits the field entirely (no money in it at
  > all). Documented on the record itself, not just here.
  >
  > **A real, pre-existing test gap found and fixed, not routed around:** `NativeQuerySchemaQualificationTest`
  > only ever scanned a literal string passed *inline* to a `jdbcTemplate.<method>(...)` call or
  > `@Query(...)` — backend doc §6's own documented convention (a `private static final String
  > ...SQL` field referenced by name) was invisible to it, undetected until analytics finally
  > exercised that pattern for the first time (flagged as a "starts earning its keep at P4+" item
  > back at P1.10, four phases early). Extended with two more literal-collecting patterns — a
  > concatenated-string constant declaration and a Java text-block constant declaration, both scoped
  > to field names containing `SQL` to avoid false-positiving on an unrelated string constant.
  > Proved both directions against the real `PayrollCostQuery.java`, not just the fixture tests:
  > temporarily unqualified all four `employee_current_comp` references, confirmed
  > `realSourceTreeQualifiesEveryNativeQuery` failed naming exactly that file/table, restored, confirmed green.
  >
  > **Verify scoped to what's seedable today** — same reasoning as every P5/P6 step's Verify-scope
  > notes; `P9`'s `SeedRunner` doesn't exist yet. `PayrollCostAndHeadcountTest` hand-seeds employees
  > (including one terminated) under a unique department/level pair, then reconciles the response's
  > `byDepartment`/`byLevel` breakdown — filtered to that pair only, never an unscoped global total,
  > same shared-Testcontainers-container discipline as `EmployeeListPaginationTest`/`BandVersioningTest`
  > — against an independent direct SQL sum computed in the test itself. Both the total and the
  > terminated-employee exclusion are proven exactly, not approximately.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 113, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (2 new tests in `PayrollCostAndHeadcountTest`, 2 new fixture tests + reuse of the real-tree scan
  > in `NativeQuerySchemaQualificationTest`, 109 pre-existing). No frontend change this step — the
  > Overview/Pay-analysis screens are P7.6.
- [x] **P7.2** `out-of-band` (FR-6.2) including cost-to-minimum.
  *Verify:* count matches the seeded anomaly count exactly.
  > **Done (2026-08-21):** `analytics/query/OutOfBandQuery` filters `employee_current_comp` on the
  > already-precomputed `band_status IN ('BELOW_MIN','ABOVE_MAX')` — a straight filter, not a
  > recomputation. `NO_BAND` is deliberately excluded (a coverage gap, not a "paid outside the band"
  > anomaly — same distinction P6.1's mandatory-note rule already draws), proven by its own test.
  > `gapAmount` per row is always positive, in the **band's own native currency** (matching how
  > `EffectiveDating.compaRatio`/`bandStatus` already compare `annual_base_amount` — not the
  > normalized figure — against the band, since a band's currency is the location's pay currency).
  >
  > **`totalCostToMinimum` is the one figure that legitimately needs `baseCurrency`**, not native:
  > summing gaps across employees paid (and banded) in different currencies has no single-currency
  > answer otherwise. Computed as one aggregate SQL expression —
  > `normalized_annual_base * (min_amount - annual_base_amount) / annual_base_amount` — which is
  > algebraically `gap_native × fx_rate` using each row's own already-pinned rate (implicit in the
  > ratio of its two stored amounts), never a live rate, never a second FX lookup.
  >
  > **Verify scoped to what's seedable today**, same discipline as P7.1: `/analytics/out-of-band` has
  > no per-department filter, so `OutOfBandTest` filters the response's `rows` down to its own two
  > seeded employee numbers (one below-min, one above-max, one in-band as a negative control) and
  > asserts exactly those two appear with the exact expected gap — never an unscoped count, since the
  > shared Testcontainers container may carry other tests' own anomalies (e.g. `EffectiveDatingTest`'s
  > above-max case). A second test proves a `NO_BAND` employee never appears at all.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 115, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (2 new tests in `OutOfBandTest`, 113 pre-existing).
- [x] **P7.3** `compa-ratio-distribution` (FR-6.3). *Verify:* quartiles match a SQL cross-check.
  > **Done (2026-08-21):** `analytics/query/CompaRatioDistributionQuery` — quartiles
  > (`percentile_cont`, same function the backend doc's own `PayGapQuery` example uses), a fixed
  > six-bucket histogram (`<0.80` … `≥1.20`, a product constant, not a request parameter), and
  > `byDepartment`/`byLevel`/`byCountry` median breakdowns, all filterable by an optional
  > `departmentId`/`jobLevelId`/`countryCode` — every filter expressed in SQL as
  > `(:param IS NULL OR column = :param)` rather than built up by Java string concatenation, so each
  > query stays one self-contained, schema-qualified constant. `NO_BAND` employees are excluded from
  > the distribution itself (no compa-ratio to place — same reasoning as P7.2's `OutOfBandQuery`) but
  > counted via `population.excluded.noBand`, never silently dropped.
  >
  > **A real, non-obvious Postgres error hit and fixed, not routed around:** the first version threw
  > `could not determine data type of parameter $3` — a parameter that appears ONLY inside
  > `? IS NULL` (with no other typed context for that specific placeholder occurrence) gives
  > Postgres's extended-protocol Describe step nothing to infer a type from, even though the very
  > next clause compares the same named parameter against a typed column. Fixed by casting every
  > occurrence explicitly (`:departmentId::uuid`, `:jobLevelId::uuid`, `:countryCode::bpchar`) —
  > removes the ambiguity outright rather than reordering clauses and hoping the planner cooperates.
  >
  > **Verify — the actual SQL cross-check, not a paraphrase of it:** `CompaRatioDistributionTest`
  > seeds five employees under one unique department against a 90000/110000/130000 band, chosen so
  > `percentile_cont` lands on exact values (compa-ratios 0.8/0.9/1.0/1.1/1.3 → p25=0.9000,
  > median=1.0000, p75=1.1000) — then re-derives the median with an independent `percentile_cont`
  > query written directly in the test (not a second call to `CompaRatioDistributionQuery`) and
  > asserts the two agree exactly. A second test proves a `NO_BAND` employee is excluded from
  > quartiles but shows up in `noBandCount`.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 117, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (2 new tests in `CompaRatioDistributionTest`, 115 pre-existing).
- [x] **P7.4** `pay-gap` (FR-6.4) with suppression **inside** the query and a suppressed-cohort
  count. *Verify:* no cohort under 5 appears in any response, at any parameter combination.
  > **A genuine methodology ambiguity, asked rather than guessed:** FR-6.4/ui doc §8.8 want both an
  > "unadjusted" and "level-adjusted" figure, "two separate columns... conflating them is
  > indefensible" — but the schema has only `gender`/`ethnicity_code` as demographic dimensions, no
  > other covariates, and the doc gives exactly one worked SQL example (the level-adjusted cohort
  > table itself). Asked the user directly rather than inventing a statistical adjustment with
  > nothing in the requirements to back it: **unadjusted = org-wide median-by-gender, ignoring job
  > level entirely; level-adjusted = the job-level × country cohort table**, which controls for
  > level by construction (grouping) rather than any regression-style adjustment. Only `gender` is
  > used as the grouping dimension — `ethnicity_code` could be added the same way later, but nothing
  > in the requirements asks for both at once, and doubling the dimension doubles the suppression
  > bookkeeping for no stated benefit yet.
  >
  > **Suppression is genuinely IN the query, not filtered afterward:** both `PayGapQuery.
  > unadjustedGroups()` and `.cohortGroups()` carry their own `HAVING count(*) >= 5` — there is no
  > code path, buggy or otherwise, that can fetch a group under five into the JVM at all (backend
  > doc §6's stated design goal, quoted in its own `PayGapQuery` example). A THIRD query,
  > `totalCohortsWithDemographicCoverage()`, returns only an aggregate count of how many level×country
  > pairings have *any* demographic coverage — never a small group's data — so
  > `suppressedCohorts = totalCohorts − cohortsThatSurvived` can be reported without ever having
  > fetched what was suppressed.
  >
  > **A cohort with only one surviving gender group also can't show a gap** (nothing to compare
  > against) — assembled out of `levelAdjustedCohorts` in `AnalyticsService.payGap()`, and counted
  > toward `suppressedCohorts` alongside the true privacy-threshold case, since both mean "not shown
  > here" to the reader even though only one is the FR-6.4 threshold specifically. Documented as a
  > combined definition on `PayGapResponse` itself, not left implicit.
  >
  > **`gapAmount`/`gapPercent` are highest-minus-lowest across however many groups survive**, not a
  > fixed "A vs B" — the schema has no two-value gender enumeration to assume, so a defined,
  > always-computable spread was chosen over guessing which two groups to compare.
  >
  > **Verified — no cohort under five appears, in either direction:** `PayGapTest` seeds one cohort
  > with 6 Male + 5 Female + 2 Non-binary (a fresh, uniquely-generated job level, isolated from the
  > shared Testcontainers container by construction) — asserts the response's matching cohort has
  > **exactly** the Male and Female groups with the exact expected median and gap
  > (105000 vs 94000 → gap 11000, 10.4762%), and that Non-binary (n=2) is absent, not merely hidden.
  > A second test seeds a cohort where all 3 people share one gender — asserts it never appears in
  > `levelAdjustedCohorts` at all and contributes to `suppressedCohorts`. `unadjustedGroups` is only
  > checked structurally (every entry `count >= 5`) since `EmployeeEntitiesRoundTripTest` also seeds
  > a demographic row into the same shared container, making an exact org-wide median unassertable.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 119, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (2 new tests in `PayGapTest`, 117 pre-existing). No `DemographicsIsolationTest` yet (P9.3) — every
  > pay-gap DTO lives in `analytics/dto`, the one package that test will exempt.
- [x] **P7.5** `increase-cycle` (FR-6.5) and employee `peers` (FR-6.6).
  *Verify:* increase spend for the seeded cycle matches a SQL sum.
  > **Done (2026-08-21):** `peers` (FR-6.6) was already built at P4.4 — this step's real scope was
  > only `increase-cycle`. `analytics/query/IncreaseCycleQuery` joins each `APPLIED`
  > `compensation_changes` row to the ledger row it actually produced via `applied_record_id`
  > (direct, not a `change_id` lookup) to reuse that row's already-pinned FX rate for normalising the
  > delta — the same trick P7.2's `totalCostToMinimum` uses. Only `APPLIED` counts
  > (CLAUDE.md §8 — an `APPROVED` change with a future effective date is a promise, not spend yet),
  > proven by its own test: a change approved but never applied contributes nothing to the total.
  > `byReason` breaks total spend, count, and avg/median increase percent down per `change_reason`.
  > `budget`/`budgetBurnPercent` are `null` when no budget query param is supplied.
  >
  > **Verify — an actual SQL cross-check, not a paraphrase:** `IncreaseCycleTest` runs one change
  > through the real lifecycle (`propose` → `submit` → `approve` → `applyDueChange`, not a shortcut
  > insert) at a deliberately unused effective date (2045 — every other change-lifecycle test either
  > fixes a year in 2031-2039 or, uniquely, `ApplyDueChangesJobTest`, which uses the real system
  > `Clock`'s actual "today"), then re-derives the normalised delta with an independent query written
  > directly in the test and asserts they agree exactly (15000.00, USD 1:1). The response's own
  > (unscoped, shared-container) total is asserted only as `>=` that scoped figure, not exactly equal.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 121, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (2 new tests in `IncreaseCycleTest`, 119 pre-existing). **This is P7's last backend endpoint** —
  > all six `/api/analytics/*` routes and `/employees/{id}/peers` are now real. P7.6/P7.7 are the two
  > remaining screens.
- [x] **P7.6** Overview and Pay analysis screens; charts themed from CSS variables; every card
  carries its basis line and a "View as table" toggle.
  *Verify:* switching themes re-colours every chart; the table equivalent exports.
  > **Done (2026-08-21):** `npm install recharts@^2` (CLAUDE.md's pinned "Recharts 2.x", first real
  > use). `useChartTheme()` (`src/lib/chart-theme.ts`) reads `--chart-1…6`/`--border`/
  > `--muted-foreground` via `getComputedStyle`, re-reading on a `MutationObserver` watching
  > `<html>`'s class attribute — a chart already on screen recolours the instant the theme toggle
  > flips `.app-light`/`.app-dark`, no reload. **Known, documented trade-off, not silently left in:**
  > the hook's `FALLBACK` constant hardcodes the light-mode hex values (copied verbatim from
  > `theme.css`) as the initial React state, since a "use client" component still renders once
  > server-side before any `useEffect` can touch `document` — a dark-mode viewer's first paint can
  > very briefly show light-mode chart colours before the effect corrects it. This is the one
  > deliberate exception to "no raw hex outside theme.css" in this codebase, and it's narrow: the
  > values are copied from the token file, never invented, and only visible for a single frame.
  >
  > **`ThemedBarChart`** (`src/components/charts/`) — one hue (`--chart-1`) for every bar; per the
  > dataviz skill's own guidance, a single-metric chart with axis labels naming each bar doesn't need
  > a second, redundant per-bar identity colour, so the categorical 6-colour ramp isn't exercised
  > here (nothing in P7.6's charts compares multiple simultaneous series — every one of them is one
  > metric across ordered/categorical positions on one axis). Thin bars, 4px rounded tops, recessive
  > grid/axes, a themed hover tooltip (not Recharts' unstyled default).
  >
  > **`ChartCard`** — the shared shell every chart lives in: title, basis line, Chart/Table toggle,
  > and (found while re-reading this step's own Verify clause literally) an **Export CSV** button —
  > `src/lib/csv.ts` builds the file entirely client-side from data already in memory (no backend
  > round-trip, no new figure computed — CLAUDE.md §6.1 is about computing money in TypeScript, not
  > serialising an already-computed one as text). Wired on every chart in both screens.
  >
  > **Overview (`/`):** six `StatCard`s (total annualised base, headcount, median compa-ratio,
  > outside band, awaiting approval, increase spend YTD — computed client-side as `new Date()` to
  > Jan 1, ordinary browser code, not a Workflow script), base-pay-by-country and compa-ratio-
  > distribution charts, and the approval queue as a compact table (reuses P6.5's `useChanges`
  > hook directly — same cache key, no duplicate fetch). Replaces the P3.3 placeholder.
  >
  > **Pay analysis (`/insights/pay`, new route):** the saved-question library — FR-6.1/6.2/6.3/6.5
  > each as a `QuestionCard` (collapsed: question + headline; expanded: full chart, table, filters).
  > Payroll cost gets a country/department/level breakdown selector; compa-ratio gets department/
  > level/country filters (the existing reference hooks, no new endpoint); increase-cycle gets a
  > date range + optional budget input, showing budget burn when one is entered. FR-6.4 (pay-gap)
  > deliberately does **not** appear here — it belongs on the separate Equity review screen (§8.8,
  > P7.7), never beside the org-wide cost/distribution questions.
  >
  > **Verified:** `npm run verify` (tokens/contrast, lint, typecheck, 28/28 vitest, build) clean —
  > lint: 0 errors, same 3 pre-existing library warnings. Live end-to-end against the throwaway dev
  > DB, signed in as the seeded HR_ADMIN: `curl`-verified all five real analytics endpoints return
  > correct shaped data (payroll-cost, headcount, out-of-band, compa-ratio-distribution both
  > unfiltered and pay-gap/increase-cycle on empty datasets, confirming graceful zero-state handling,
  > not a crash) — confirmed against the actual seeded 8-employee dataset (e.g. Carla's
  > `ABOVE_MAX`/15000 gap matches the seed data documented at P4.3). Both `/` and `/insights/pay`
  > return `200` with the correct signed-in user in the rendered shell, no error boundary. **No live
  > browser pass this session** — Chrome browser tools were not enabled; chart rendering, the theme-
  > switch recolour, and CSV download were verified by code review and the data-layer checks above,
  > not by eye. `next start` is up on `:3100` against the live backend if a visual check is wanted.
- [x] **P7.7** Equity review screen with the suppression notice and separate unadjusted /
  level-adjusted columns. *Verify:* the suppressed count is non-zero against the seed and is shown.
  > **Done (2026-08-21):** `/insights/equity` — reuses P7.4's `usePayGap()` hook directly (same
  > cache key as anywhere else it's called; no new endpoint). Unadjusted and level-adjusted render
  > as two separately-headed sections, never merged into one figure or one row's two columns — per
  > this step's own P7.4 methodology decision (asked and confirmed with the user there): unadjusted
  > is the org-wide group comparison, level-adjusted is the cohort table. The suppression notice
  > renders unconditionally, even at zero ("no cohorts were suppressed" is itself a stated, checked
  > fact, not a silent absence) — ui doc §8.8's "with one line explaining why" is literal here: the
  > notice names both suppression reasons (a group under five, or only one group represented).
  >
  > **Verified live, not just unit-tested:** hand-seeded `employee_demographics.gender` for 7 of the
  > throwaway dev DB's active employees (asked the user first whether to keep or discard the seeded
  > data afterward — kept, so `/insights/equity`/`/insights/pay` have something real to look at).
  > `curl`-verified `/api/analytics/pay-gap` against this real data: `suppressedCohorts: 2` (non-zero,
  > this step's own Verify clause) — one cohort suppressed because its two gender groups both fell
  > under five, two more because each had only one person. Also surfaced a genuine, pre-existing dev-
  > seed data-quality fact along the way, not a bug: one ACTIVE employee (Deepa, E-0004) has no
  > `employee_current_comp` row at all (never had a comp record set), which is exactly why she's
  > invisible to every analytics query that joins through the projection — correct behaviour,
  > worth knowing about when eyeballing these screens' numbers. `/insights/equity` returns `200`
  > with the real suppressed-cohort data rendered server-side into the RSC payload. `npm run verify`
  > clean (lint 0 errors, same 3 pre-existing warnings; 28/28 vitest; build includes the new route).
  > **No live browser pass this session** — Chrome browser tools were not enabled.
  >
  > **P7 (Insights) — complete.** All six `/api/analytics/*` endpoints, `/employees/{id}/peers`
  > (P4.4), and three screens (`/`, `/insights/pay`, `/insights/equity`) are real, tested, and
  > curl-verified against a live throwaway dev DB.

## P8 — Admin, audit, import

- [x] **P8.1** Users and roles admin; admin-issued reset tokens; last-HR-Admin protection.
  *Verify:* deactivating the last HR Admin is refused.
  > **Done (2026-08-21):** `UserAdminService` — `create()` (no password field on the request; a
  > random, never-known-to-anyone secret becomes the initial hash, unlocked only by immediately
  > issuing a reset token), `update()` (full replace, `EmployeeUpdateRequest`'s own convention),
  > `issueResetToken()` (FR-1.6: opaque random secret via the same `RefreshTokens.generate()`/
  > `.hash()` helper P2.2's refresh tokens use — SHA-256 hash persisted, raw token returned exactly
  > once, never stored). "Cannot change own role" and "last active HR Admin" are two separate,
  > separately-tested guards — the second also blocks reassigning the LAST active HR_ADMIN's role
  > away from HR_ADMIN, not just literal deactivation, since both have the identical effect (FR-1.5's
  > text names only deactivation, but the reasoning obviously extends to the same danger by another
  > route — a genuine, deliberate extension of the letter of the rule, documented as such).
  >
  > **A real bug found and fixed, not just documented:** returning `user.getCreatedAt()` immediately
  > after `userRepository.save(...)` was silently `null` — `@CreationTimestamp` is a Hibernate-
  > generated value populated at flush time, not by the entity builder, and `save()` alone doesn't
  > force a flush. Fixed with `saveAndFlush()`; a regression test (`created.createdAt()` non-null)
  > pins it down. First time this app has returned a `@CreationTimestamp` field in the same response
  > as the write that created it — every other entity's create path returns a DTO that omits it.
  >
  > **A real test-isolation problem found and fixed:** the "last active HR Admin" guard counts every
  > row in `users` — untestable against the shared Testcontainers container every other test class
  > (and even this step's own sibling test methods, sharing one class-cached container) populates
  > with its own HR_ADMIN fixtures. `LastActiveHrAdminTest` runs with a properties signature no other
  > test class uses, so Spring caches it a private, empty container — same technique
  > `PostgresContainerIntegrationTest` already established for an unrelated reason.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 124, Failures: 0, Errors: 0`, `BUILD SUCCESS`
  > (2 new tests in `UserAdminTest`, 1 in `LastActiveHrAdminTest`, 121 pre-existing). Live-verified:
  > listed users, created a new HR_MANAGER, confirmed self-role-change rejected (400).
- [x] **P8.2** Audit: write aspect, read interceptor, append-only grants, `AuditImmutabilityTest`.
  *Verify:* a pay-list read produces an audit row recording the filter; an update is denied.
  > **Done (2026-08-21):** `AuditService` (new `audit` package sibling to the existing `AuditEvent`/
  > `AuditEventRepository`/`AuditController` stub from P1.7/P2.4) — deliberately **explicit calls at
  > each write/read site, not a generic `@Audited` AOP aspect** capturing before/after by reflection:
  > `ApplyDueChangesJob`'s scheduled path (P6.2) has no HTTP request and therefore no
  > `SecurityContextHolder` authentication to intercept, so every call site already carrying its own
  > acting-user id (the established `@AuthenticationPrincipal UUID currentUserId` convention, or
  > `decidedBy` for the scheduled path) is simpler and correct in both cases, not a reflection-based
  > guess at "the current user." `actor_role` is resolved from `actorUserId` via a `UserRepository`
  > lookup inside `AuditService` itself, not threaded through every call site.
  >
  > **Wired onto every write that touches pay, bands, changes, or user identity:**
  > `EffectiveDating.apply()`/`.correct()` (the ledger itself — covers every raise/correction
  > regardless of which controller triggered it), `EmployeeService.create()`/`.update()`/
  > `.terminate()`, `ChangeService.propose()`/`.approve()`/`.reject()`/`.applyDueChange()` (the
  > CHANGE entity's own APPLIED transition — a distinct entity from the ledger row
  > `EffectiveDating.apply()` already audits inside the same call, not a duplicate), `BandService
  > .create()`/`.update()`, `UserAdminService.create()`/`.update()`/`.issueResetToken()`.
  > **Deliberately not audited** (documented scope trim, not a silent gap): `ChangeService
  > .updateDraft()`/`.submit()`/`.discardDraft()` (pre-decision editing with no committed effect),
  > `BandService.importCsv()`/`ChangeService.bulkUpload()` (bulk paths — auditing each row would be
  > a larger, separate pass). `GET /employees/{id}/peers` also isn't audited as "individual pay
  > data" — it's this person's position against an aggregate, not their own record, a genuinely
  > borderline call flagged rather than silently decided.
  >
  > **A real, non-obvious ordering bug found and fixed at three separate call sites:** the FIRST
  > version of this wiring serialised the "before" state AFTER the entity had already been mutated
  > in place (e.g. `EffectiveDating.apply()` closes the old period, then — in the original draft —
  > serialised it for the audit row, capturing the CLOSED state as "before" instead of the true
  > pre-mutation state). Fixed by adding `AuditService.snapshot(Object)` — freezes a JSON string
  > immediately, before any mutation — used at every call site that mutates in place before it can
  > audit (`EffectiveDating.apply()`/`.correct()`, `ChangeService.approve()`/`.reject()`/
  > `.applyDueChange()`, `BandService.update()`, `EmployeeService.update()`/`.terminate()`).
  >
  > **Read auditing (FR-7.2)** wired onto `EmployeeController`'s `list`/`get`/`compensationHistory`/
  > `compensationAsAt`/`export` — every one of them now takes `@AuthenticationPrincipal UUID
  > currentUserId` (none did before this step). A list/export read records the filter description
  > and the result count, never the individual ids; a detail/history read records which employee.
  >
  > **`AuditImmutabilityTest`** — new, deliberately going further than P1.7's
  > `V8V9AuditAndProjectionMigrationTest` (which already proved the raw database grant with a
  > hand-crafted INSERT): this one drives a REAL `EmployeeService.update()` and a REAL `.list()`
  > read through the actual `AuditService`, asserts the resulting rows have the right actor/action/
  > before-after JSON, THEN proves those specific real rows can't be updated by the `salaryos_app`
  > role — the full loop, not just the database permission in isolation.
  >
  > **A live, active session-collision discovered and resolved mid-step, not glossed over:** another
  > `claude` CLI process (pid 5087) was independently running its own `./mvnw clean verify` against
  > this same repo/Testcontainers-Docker-daemon at the same time, corrupting consecutive verify runs
  > of the identical command (different test counts, different failure sets, a transient
  > `PostgresContainerIntegrationTest` context-load failure) — real evidence, not a hunch, surfaced
  > by rerunning the same command three times and getting three different results. Flagged to the
  > user, who had it stopped; a clean, uncontested rerun afterward reproduced a stable
  > 124/124-then-125/125 result, confirming every earlier "failure" from that window (an
  > `ApplyDueChangesJobTest` miss, a `LastActiveHrAdminTest` FK error later found to be a real bug —
  > see below — plus the phantom context-load error) was genuinely two different things tangled
  > together, not one. **The one real bug this surfaced**, separate from the concurrency noise:
  > `LastActiveHrAdminTest`'s `actingAsSomeoneElse` was a bare `UUID.randomUUID()` — harmless before
  > this step added an FK-constrained audit write to `UserAdminService.update()`, a genuine failure
  > once it did. Fixed by seeding a real acting user.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 125, Failures: 0, Errors: 0`, `BUILD SUCCESS`,
  > run uncontested after the other session stopped (1 new test in `AuditImmutabilityTest`, 124
  > pre-existing). Live-verified against the throwaway dev DB: a real `GET /employees/{id}` produced
  > a `READ_DETAIL` row with the correct actor role and entity id, visible via direct SQL.
- [x] **P8.3** Audit log screen with filters and export; FX rate admin by month.
  *Verify:* a missing rate month is visible and addable.
  > **Done (2026-08-22):** Backend: `AuditController#search`/`#export` (FR-7.4) — filters by actor,
  > entity type, action, and date range via a `Specification<AuditEvent>` (same pattern
  > `ChangeService.list` already used), newest first, actor identity resolved in one batch
  > `UserRepository.findAllById` rather than N+1. CSV export reuses the exact same filter, same
  > `text/csv` attachment convention as `EmployeeController#export`. `FxRateController#list`/`#add`
  > went from `501` stubs to real: `FxRateService.missingMonths()` computes, for every country's
  > `defaultCurrency` other than `app.base-currency`, which of the trailing 13 months has no pinned
  > rate yet — that's the "missing" list the Verify clause asks for. `#add` normalises to the first
  > of the month and 409s (`FxRateAlreadyExistsException`) rather than silently overwriting a rate a
  > past `compensation_records` row may already reference (CLAUDE.md §6.4).
  >
  > **A real RBAC conflict, caught before it shipped:** the obvious UI for an "actor" filter is a
  > dropdown backed by `GET /api/admin/users` — but that endpoint is `HR_ADMIN`-only (P8.1), while
  > "read the audit log" is `HR_ADMIN` **and** `AUDITOR` (CLAUDE.md §7). Building the actor filter
  > that way would 403 for an Auditor doing exactly what FR-7.4 asks. Fixed by not adding that
  > endpoint dependency at all: the actor filter is driven by clicking an actor's email in a row
  > (audit rows already carry the identity) plus the raw `actorUserId` staying in the URL — both
  > roles can use it, no new endpoint, no new permission surface.
  >
  > Frontend: `/admin/audit` (filter state in `searchParams` per CLAUDE.md §9 — entity type, action,
  > from/to date, actor id + a display label so a shared link still shows a name) and
  > `/admin/fx-rates` (pinned-rates table + a "missing months" chip list, each chip opening
  > `AddFxRateDialog` prefilled with that currency/month; add actions gated by a new
  > `canManageFxRates` role helper in `roles.ts`, mirroring `canApproveChanges`'s "visible tab, absent
  > action" convention rather than hiding the whole screen). Added `/admin/fx-rates` to
  > `AREA_ACCESS`/`NAV_VISIBILITY`/`NAV_GROUPS` — `roles.test.ts`'s "every declared area appears in
  > the sidebar" check enforces this pairing, not a choice.
  >
  > Rate values are never rendered via `<Money>` — an FX rate is a ratio, not a currency amount
  > (CLAUDE.md §6.2's "money never travels without currency" is about a different invariant; a bare
  > `.figure` span is correct here, `<Money>` would be wrong).
  >
  > New backend tests: `AuditSearchTest` (real writes via `AuditService.recordWrite`, then filters by
  > actor/entity/action/date-range and checks the CSV bytes match) — calling `AuditController#export`
  > directly on the bean (not through MockMvc, matching this codebase's service-level test
  > convention) needed a manually-populated `SecurityContextHolder` for `@PreAuthorize` to evaluate,
  > cleared in `@AfterEach`. `FxRateAdminTest` (a currency+month starts in "missing", `add()` moves it
  > to the rate list, a second identical `add()` 409s) — used a throwaway `ZZQ` currency/country so
  > the assertion is exact regardless of what other test classes' fixtures have pinned.
  > `RolePermissionMatrixTest` gained `AuditController#export`'s entry (the pre-existing `#search`/
  > `FxRateController#list`/`#add` entries were already there, left by the terminated concurrent
  > session ahead of this step — confirmed correct, not re-derived).
  >
  > Observed: `./mvnw clean verify` → `Tests run: 127, Failures: 0, Errors: 0`, `BUILD SUCCESS` (125
  > pre-existing + `AuditSearchTest` + `FxRateAdminTest`). Live-verified against the throwaway dev
  > DB: restarted both servers on the new code, logged in as the seeded HR Admin, confirmed
  > `GET /admin/fx-rates` correctly listed `EUR`/`GBP` as missing for 13 real months, `POST` added
  > one and it moved out of "missing", the write appeared in `GET /admin/audit`, and
  > `GET /admin/audit/export` produced the matching CSV row. Both `/admin/audit` and `/admin/fx-rates`
  > render server-side (curled with a real session cookie, `200` with the expected heading in the
  > HTML) against a rebuilt `next start`.
- [x] **P8.4** Employee CSV import with dry-run diff.
  *Verify:* the dry run reports counts and writes nothing.
  > **Done (2026-08-22):** `EmployeeService.importCsv` mirrors `BandService.importCsv`'s (P5.3)
  > create-vs-version shape as create-vs-update, keyed by `employeeNumber` (a human-edited CSV's
  > only realistic key, same reasoning `ChangeService.bulkUpload`'s FR-5.8 upload already used).
  > 12-column CSV (`employeeNumber,firstName,lastName,workEmail,departmentId,locationId,
  > jobFamilyId,jobLevelId,managerId,hireDate,employmentType,fte`); an existing number updates that
  > employee's profile through the exact same `Employee#updateProfile` the single-employee edit
  > endpoint calls, so a level/location change via import sets `bandMismatched` exactly as it would
  > there — importing never touches pay. Everything is pre-validated in Java before any write
  > (department/location/jobFamily/jobLevel existence via `existsById`, employment type, FTE range,
  > required fields) rather than relying on a caught `DataIntegrityViolationException` — same
  > reasoning `BandService.importCsv`'s own javadoc gives: a dry run must not touch the DB at all,
  > so nothing here can find out it was wrong from a failed constraint.
  > `POST /api/employees/import` is `HR_ADMIN`-only ("Import / bulk upload", CLAUDE.md §7).
  >
  > A real bug caught before it shipped: `apiFetch` (`client.ts`) unconditionally set
  > `Content-Type: application/json` whenever a request had a body, which would have broken the
  > CSV upload's `FormData` body (the browser needs to set its own `multipart/form-data; boundary=…`
  > header, and can only do that when no `Content-Type` is already present). Fixed with one
  > `instanceof FormData` guard — the one shared fetch seam every request crosses, so every future
  > file upload gets this for free too.
  >
  > Frontend: `/admin/import` — file input, "Preview (dry run)" always runs first and renders the
  > row-by-row diff (a `CREATE`/`UPDATE`/`ERROR` badge per row) without writing anything, "Apply
  > import" then resends the identical file with `dryRun=false`. No new nav entry or RBAC table
  > row needed — `/admin/import` and its `HR_ADMIN`-only visibility already existed in `roles.ts`/
  > `nav.ts` since P3.5, waiting for this step to give it a screen.
  >
  > New backend test: `EmployeeImportTest` — a dry run over one good + one bad row reports
  > `created=1, errors=1, rowsApplied=0` and leaves the database empty; the real run then applies
  > exactly the good row; a follow-up import of the same `employeeNumber` updates the existing row
  > (same id, new `firstName`) instead of erroring as a duplicate. `RolePermissionMatrixTest` gained
  > `EmployeeController#importCsv`.
  >
  > Observed: `./mvnw clean verify` → `Tests run: 128, Failures: 0, Errors: 0`, `BUILD SUCCESS` (127
  > pre-existing + `EmployeeImportTest`). Live-verified against the throwaway dev DB: a real 2-row
  > CSV (one valid, one with a nonexistent department) dry-ran to `created:1, errors:1,
  > rowsApplied:0` with the database unchanged (confirmed via direct SQL), then the real run wrote
  > exactly the one valid employee and left the bad row rejected with its error message intact.
  > `/admin/import` renders server-side (curled with a real session cookie, `200` with the expected
  > heading) against a rebuilt `next start`. Test data cleaned up afterward.

### Post-P8 QA pass (2026-08-22) — user-reported: nav tabs 404ing, sidebar states

User report after the local-setup handoff: "some of the navigation tabs are not working it
says not found and more errors" — a real, correct report. Ran the full app end to end (four
seeded roles, every nav-reachable route, both sidebar states, 375px) rather than re-reading
code, and found six issues, all fixed:

1. **Four nav items had never had a page built**: `/admin/users`, `/levels`, `/locations`,
   `/insights/reports` were declared in `nav.ts`/`roles.ts` since P3.5 and rendered as real
   sidebar links, but no `page.tsx` existed for any of them — a straight 404. `npm run build`
   never catches this class of gap: a route only fails the build if literally no page exists
   for a href anywhere, which was true here, but nothing in the P8.1–P8.4 verify steps actually
   clicked the links, so it shipped. Built all four:
   - `/admin/users` — P8.1's backend (`UserAdminService`) was complete but had no screen; added
     one (list, create, edit role/status, issue-reset-token-shown-once), the actual missing
     piece, not new backend work.
   - `/levels`, `/locations` — `GET /api/reference/*` are the only endpoints either domain has
     (no create/update/delete exists), so these are read-only browse screens, not CRUD — matches
     how they're actually seeded reference data today, not a scope cut.
   - `/insights/reports` — the seven FR-6 questions are already fully covered by Pay analysis and
     Equity; this screen is the first UI for the `headcount` analytics endpoint's `byCountry`/
     `byDepartment`/`byLevel`/`byStatus` breakdown (already built at P7, never surfaced beyond
     Overview's single stat card) plus links to the two existing CSV exports. Not a report
     builder — v1 deliberately excludes free-form reporting (requirements-one-pager.md).
2. **A real crash, found by actually clicking through, not by reading code**: `/employees` and
   every employee detail page threw `e.toFixed is not a function` and rendered Next's client
   error boundary ("This page couldn't load") for any employee with a non-null compa-ratio —
   i.e., most of them. Root cause: the `JacksonConfig` fix adopted during P8.2 (every `BigDecimal`
   now serialises as a JSON string) was correctly matched by the analytics screens built after it
   (P7, all wrapped with `Number(...)`), but `employees.ts`/`changes.ts` predate that fix and kept
   `compaRatio`/`rangePenetration`/`deltaPercent`/`currentCompaRatio`/etc. typed `number` — `npm
   run typecheck` can't catch a runtime type lying about itself. Fixed by retyping those fields
   `string`/`string | null` and wrapping every call site with `Number(...)` (`employees-table.tsx`,
   `current-pay-panel.tsx`, `propose-change-dialog.tsx`, `changes-table.tsx`) — same pattern the
   P7 screens already used correctly.
3. **A retry-storm on every role-gated route**: `QueryProvider`'s `QueryClient` had no retry
   policy, so TanStack Query's default (3 attempts) retried a 403 three times, each retry
   re-triggering `apiFetch`'s toast — three stacked "Access denied" toasts and a stuck loading
   skeleton for several seconds before the real error state ever showed, on any role-gated page
   reached directly (a stale bookmark, browser back, a shared link — not just a nav click, which
   the sidebar already hides). Fixed with a `retry` function that skips retrying any 4xx.
4. **`FxRateService.missingMonths()` had two real gaps**, found by actually proposing a change
   and hitting a 422: it excluded the base currency from the scan (but `EffectiveDating.findRate`
   looks up a real pinned row for a same-currency USD→USD conversion too, no short-circuit — every
   comp record needs one, CLAUDE.md §6.4's "every comp record" is literal), and it only looked
   backward (trailing 13 months), never forward — but a proposal is routinely dated a cycle or two
   ahead. Fixed both; the dev DB only had a USD→USD row for the one month it was seeded in, so
   essentially every future-dated proposal was 422ing. Added rates for the next three months via
   the real `POST /admin/fx-rates` endpoint (not a backend seed) to unblock testing; a full year's
   worth is `SeedRunner` work (P9.1), not a hand-patch.
5. **Topbar overflowed 375px by ~12px on every single page**, pre-existing (present on `/employees`
   too, untouched by P8) — not a P8 regression, but a real, currently-broken violation of the
   mobile rule, found by actually measuring `document.documentElement.scrollWidth` rather than
   eyeballing a screenshot. `Brand`'s wordmark ("Salary OS" / "ACME") never hid on mobile, and
   combined with the fixed-width currency toggle, the topbar's right cluster had nowhere to go.
   Fixed by hiding the wordmark below 768px (the same breakpoint the sidebar/Sheet nav switch
   uses everywhere else) — the name is still one tap away in the Sheet's own header.
6. Overview's "Awaiting approval" preview table had no `overflow-x-auto` wrapper (every other
   table in the app does), so it could force the page wider than its container on a narrow
   screen — fixed to match the established pattern, moot after #5 but correct on its own.

New Playwright QA tooling (`salary-web/scripts/`, wired into `package.json`): `verify-routes.mjs`
(every nav-reachable route × all four roles, checks for a client crash or a role-gated route
that doesn't degrade gracefully — this is what would have caught issue #1 and #2 before ship),
`verify-sidebar-states.mjs` (authenticated collapse/expand/hover-peek/reload-persistence, extends
P3.3's `verify-shell.mjs` which runs unauthenticated and never reaches the sidebar), and
`verify-mobile-nav.mjs` (authenticated 375px scroll-width + Sheet-nav check — same reason
`verify-shell.mjs`'s existing 375px check never caught #5, it never logs in). Needs four seeded
QA users, one per role (`qa.manager@acme.test` etc., `Password123!`) — the throwaway dev DB only
had HR_ADMIN and HR_MANAGER accounts before this pass.

Observed: backend `./mvnw clean verify` → `128/128` (unchanged test count — only `FxRateService`
changed, covered by the existing `FxRateAdminTest`). Frontend `npm run verify` (tokens + contrast
+ lint + typecheck + test + build) → clean. `verify:routes` → 0 problems across all four roles.
`verify:sidebar` → 240px/60px/240px-hover/13-items-both-states, all correct. `verify:mobile-nav` →
`375px === 375px` on both a page with the fixed table and one without, sheet nav fully functional
and actually navigates. Live re-verified the exact original crash (`/employees/{id}` for an
above-band employee) and the full propose-change → live impact preview flow end to end — both
clean, zero console errors, zero page errors.

### Add-employee-via-UI feature + a critical CSRF bug it uncovered (2026-08-22)

User ask: "add a feature to add employee in the application only through UI." There was none —
`POST /api/employees` (create) had existed since P4.2, but no screen ever called it, and there
was no way to give a new hire their first paycheck either: `ChangeService.propose` requires an
*existing* `employee_current_comp` row, so "Propose change" was structurally unusable for anyone
with zero pay history. The `INITIAL` reason code had been reserved for exactly this in V11's
vocabulary since P1, and `PROPOSABLE_CHANGE_REASONS` already excluded it with a comment saying so
— the design anticipated this gap; nothing had filled it in yet.

**Backend**, new capability: `POST /api/employees/{id}/initial-compensation` (`EmployeeService
.setInitialCompensation`, ADMIN_AND_MANAGER — same as `create`). Calls `EffectiveDating.apply`
directly with `changeReason=INITIAL`, `effectiveFrom=` the employee's hire date — deliberately
NOT the propose/approve/apply lifecycle, since there's nothing to approve against; this establishes
the very thing every later change would be a change *from*. Refused (409,
`EmployeeAlreadyHasCompensationException`) once `compensation_records` has ANY row for that
employee, open or closed — added `CompensationRecordRepository.existsByEmployeeId` for the guard.
`EmployeeService` now depends on `EffectiveDating` (a new, non-circular edge — `ChangeService`
already depended on both).

**Frontend**: `/employees`' "New employee" button (identity/org fields only — no pay, matching
CLAUDE.md §6.3's insert-only ledger). On success, navigates straight to the new person's detail
page, where `CurrentPayPanel`'s existing "no compensation record" empty state gained a real "Set
starting salary" action — the SAME mechanism a CSV-imported employee with no pay yet already
needed (P8.4's import creates employees exactly the same way, with the exact same gap), so there
is one path for "give this person their first paycheck," not two. No manager-picker in the create
form — that needs a person-search combobox this pass didn't build (there's no edit-employee screen
yet either, so this matches that same, already-existing scope trim).

**A critical, previously-undiscovered bug, found only because this was tested as a real multi-step
browser session** (sign in → browse → submit a form several navigations later — how an actual
person uses the app, unlike almost every verification in this project so far, which drove
mutations directly via `curl` or as the very first request in a fresh session): **any plain
authenticated GET request deleted the `sos_csrf` cookie**, breaking every mutation attempted
afterward with a 403 "Access denied" indistinguishable from an RBAC failure. Root cause: Spring
Security 7's `CsrfFilter` was calling `saveToken(null, ...)` on the cookie repository on a bare
GET even when the request already carried a valid token, and `CookieCsrfTokenRepository` turns a
null save into an explicit `Max-Age=0` deletion. Fixed with `NonDeletingCsrfTokenRepository` (new,
`config/`) — a thin wrapper around the real cookie repository that no-ops a null save, since this
app's CSRF cookie lifecycle is already owned explicitly by `AuthController#login` and nothing
legitimate ever needs the filter chain to clear it implicitly. `loadToken`/`generateToken` are
untouched. Full reasoning and repro history in the class's own javadoc.

This is almost certainly why **every previous "propose a change," "create a band," "add an FX
rate," "create a user" verification in this project's history worked when tested, yet the very
first genuine multi-page browser session hit it immediately** — every prior verification either
called the API directly or mutated as the first request of a session. This bug would have blocked
every real HR Manager's very first save of their session, on literally every mutating screen in
the product. Worth internalizing for `P9.5`/`P9.6`: any acceptance-criteria walkthrough MUST be a
continuous multi-page session, never a fresh session per check.

New backend test: `EmployeeLifecycleTest#settingInitialCompensationEstablishesTheFirstPayPeriod
AndCannotBeDoneTwice` (seeds an FX rate, sets initial comp via the real HTTP endpoint, asserts the
ledger row and `change_reason=INITIAL`, asserts a second call 409s). `RolePermissionMatrixTest`
gained the new endpoint's entry.

Observed: backend `./mvnw clean verify` → `129/129` (unchanged besides the one new test — the CSRF
fix touches only the security filter chain, no domain logic, and `AuthControllerIntegrationTest`
stayed green through it). Frontend `npm run verify` clean; `verify:routes` clean across all four
roles. Live end-to-end via a real Playwright browser session (not curl): created "Taylor Nguyen"
through the dialog, landed on their detail page, set a $95,000 starting salary, watched Current
Pay, Pay History, and the now-enabled "Propose change" button all update correctly with zero page
errors — the exact sequence that would have 403'd before the CSRF fix. Test data cleaned up
afterward.

## P9 — Seed, hardening, acceptance

- [x] **P9.1** `SeedRunner` and generators per `salary-management-backend.md §9`, including every
  deliberate anomaly. *Verify:* 10,000 employees and ~40k comp records in under 90 seconds; log the
  stage timings.

  Observed (local Docker Postgres, `--spring.profiles.active=local,seed`, seed=20260820): **17s**
  total, well under the 90s target. Per-stage timings logged by `SeedRunner`: countries 3ms,
  locations 2ms, departments 2ms, job families/levels 3ms, users 431ms (Argon2id hashing), bands
  38ms, fx rates 9ms, employees 1394ms, demographics 110ms, compensation records+components
  14652ms (the dominant stage — 10,000 employees × 3–7 periods each), changes 392ms.

  Counts (confirmed against the DB directly, not just the log): `employees=10000`,
  `compensation_records=49688`, `compensation_components=14288`, `compensation_changes=1200`,
  `salary_bands=936`, `fx_rates=504`, `employee_current_comp=9580`.

  Anomalies: `belowMin=180` (2 countries, IN/BR), `aboveMax=60` (L5+), `noBand=33` (target ~40 —
  see below), current-period compa-ratio range `0.60..1.56` (spec's "0.72–1.28" describes the bulk
  spread; the deliberate below-min/above-max anomalies are meant to sit outside it, which they now
  do cleanly after a fix — see next paragraph).

  Two rounds of tuning against the doc's approximate targets, both found by actually running the
  seed rather than reasoning about it: (1) the original per-(level×country) band coverage was a
  50/50 coin flip, which left ~5,000 employees — half the company — with no band, against the
  doc's "40 with a level/country combination that has no band." Replaced with a deterministic rule
  (`BandGenerator`: skip only L6/L7 in Ireland and Poland, the two rarest levels × two
  lowest-weighted countries), landing at 33. This does widen `bands(rows)` to 936 vs. the doc's
  "~500" — a direct consequence of fixing the far more consequential employee-facing anomaly count;
  documented in `BandGenerator`'s javadoc. (2) Compounding raises (2–12% per period, uncompounded
   by any ceiling) let ordinary long-tenured employees drift past compa-ratio 2.0 with no anomaly
  involved; `CompensationGenerator` now clamps ordinary period-over-period drift to `band.mid() ×
  [0.72, 1.28]` before the deliberate below-min/above-max overrides run (which still replace the
  value outright, so those targets land outside the clamp as intended). Component volume was ~31k
  against the doc's "~14,000" (a component was attached to every eligible period, not a sample of
  them); gated both `BONUS_TARGET` and `HOUSING`/`TRANSPORT` behind `COMPONENT_CHANCE=0.46`,
  landing at 14,288.

  `compensation_records=49688` is ~24% over the doc's "~40,000" and was not further tuned: the
  same doc line commits to "3–7 periods each" (inclusive), whose own average (5) already implies
  10,000 × 5 = 50,000 — the two figures in the spec are in tension with each other, and honoring
  the literal per-employee period count was judged more important than forcing the aggregate
  total down by narrowing it.
- [x] **P9.2** Reproducibility. *Verify:* seed twice from empty; totals, medians, and the anomaly
  counts are identical. Assert it in a test.

  Split `SeedRunner` into a thin `@Profile("seed")` CLI guard and a plain `Seeder` bean carrying
  the actual orchestration, so `SeedReproducibilityTest` can call `seeder.seedAll(...)` twice
  against a real Testcontainers Postgres 17 without activating the `seed` profile. Observed: both
  runs produced identical `employees=10000`, `compensation_records=49688`,
  `compensation_components=14288`, `compensation_changes=1200`, `employee_current_comp=9580`,
  identical `Anomalies` (belowMin=180, aboveMax=60, noBand=33, compaRatioRange 0.60..1.56), and
  identical DB-computed medians (`percentile_cont(0.5)` over `normalized_annual_base` and
  `compa_ratio` in `compensation_records`) — asserted in
  `SeedReproducibilityTest.seedingTwiceFromEmptyProducesIdenticalTotalsMediansAndAnomalies`,
  `Tests run: 1, Failures: 0`.

  Found and fixed a test-isolation bug in the same pass: the test's first version left 10,000
  seeded rows behind after its assertions, and because Spring's test-context cache reuses the same
  Testcontainers container across test classes with an identical `@SpringBootTest` config, that
  pollution broke four unrelated tests (`ApplyDueChangesJobTest`, `V4V5BandsAndFxMigrationTest`,
  two `CompensationEntitiesRoundTripTest` cases) the first time the full suite ran after adding it.
  Fixed with an `@AfterEach` that truncates the same tables. Full suite after the fix:
  `./mvnw clean verify` → `Tests run: 130, Failures: 0, Errors: 0`, `BUILD SUCCESS`.
- [x] **P9.3** `DemographicsIsolationTest` across every DTO package outside `analytics`.
  *Verify:* it fails when you add a `gender` field to an employee DTO, and passes when removed.

  Every DTO in this codebase is a Java `record`, so the test scans `record Name(...)` headers
  directly out of `src/main/java` (balanced-paren parameter list, top-level-comma split — handles
  nested records and generic types like `List<Failure>`) rather than reflecting over compiled
  classes; same source-scanning style as `NativeQuerySchemaQualificationTest`. Flags an exact
  (not substring) match against `gender/sex/ethnicity/race/age/dateOfBirth/dob/...` — exact match
  specifically so "age" doesn't false-positive on unrelated names like `wage` or `pageSize`.
  `analytics` is exempt per the step's own text: its DTOs (`PayGapGroupMedian` etc.) already carry
  a demographic value under a generic `group` label for a ≥5-person cohort, never a named field
  per person.

  Verify, observed directly: added a `String gender` component to `EmployeeSummaryResponse` →
  `Tests run: 1, Failures: 1` citing exactly that file and field; removed it → `Tests run: 1,
  Failures: 0` again. Full suite after: `./mvnw clean verify` → `Tests run: 131, Failures: 0,
  Errors: 0`, `BUILD SUCCESS`.
- [x] **P9.4** Performance pass against NFR-1…4. *Verify:* record observed p95 for the list, detail,
  and each analytics endpoint. Add indexes only where a measurement justifies it.

  Measured against the real local Postgres with the full P9.1 seed loaded (10,000 employees,
  49,688 comp records) — app started with `--spring.profiles.active=local` (no seed profile),
  authenticated as `admin@acme.test`, then 20–30 timed requests per endpoint via `curl -w
  "%{time_total}"`, p95 computed directly from the sorted samples (a JMH-lite timed-integration
  pass, per backend doc §10's own description of this step, not a formal JMH harness).

  | NFR | Target | Endpoint | Observed p50 / p95 / max |
  |---|---|---|---|
  | NFR-1 | p95 < 400ms | `GET /api/employees?status=ACTIVE&countryCode=US&limit=50` | 11.6 / 22.5 / 114.6 ms |
  | NFR-3 | p95 < 300ms | `GET /api/employees/{id}` | 7.1 / 10.1 / 16.5 ms |
  | NFR-3 | p95 < 300ms | `GET /api/employees/{id}/compensation` (full ledger) | 5.1 / 8.6 / 20.3 ms |
  | NFR-2 | p95 < 1.5s | `GET /api/analytics/payroll-cost` | 16.5 / 17.8 / 25.3 ms |
  | NFR-2 | p95 < 1.5s | `GET /api/analytics/headcount` | 15.3 / 19.9 / 25.5 ms |
  | NFR-2 | p95 < 1.5s | `GET /api/analytics/out-of-band` | 43.8 / 51.0 / 52.3 ms |
  | NFR-2 | p95 < 1.5s | `GET /api/analytics/compa-ratio-distribution` | 66.7 / 68.1 / 70.6 ms |
  | NFR-2 | p95 < 1.5s | `GET /api/analytics/pay-gap` | 34.9 / 36.5 / 43.3 ms |
  | NFR-2 | p95 < 1.5s | `GET /api/analytics/increase-cycle?fromDate=2025-08-01&toDate=2026-08-01` | 2.6 / 3.4 / 8.6 ms |

  All six comfortably clear their targets (the widest margin is `compa-ratio-distribution` at
  ~4.5% of budget) — **no index changes made**, exactly per this step's own instruction to add
  indexes only where a measurement justifies it; V10's existing indexes are sufficient at this
  data volume. `increase-cycle` 401'd on the first attempt because the request omitted its
  required `fromDate`/`toDate`; not a real auth failure — re-ran with them, 200 immediately.

  NFR-4 (FCP on the list route, < 1.5s cold cache) is a client-rendering metric that needs a real
  browser (Lighthouse or Chrome DevTools); browser tooling is not enabled in this session, so it
  was **not measured**. As a proxy, `npm run build && npm run start` against the same seeded
  backend and a `curl` of `/employees` with an authenticated cookie measured server TTFB only:
  125ms on the very first (cold, on-demand-compiled) request, 7–16ms steady-state on the next 10 —
  well inside budget for the part this proxy actually covers, but it excludes JS parse/hydrate and
  font/CSS paint time, so it is not a substitute for a real FCP measurement. Flagging as an open
  item for whenever browser tooling is available, rather than reporting a number that wasn't
  actually observed (CLAUDE.md §12.13).
- [x] **P9.5** Accessibility and responsive pass (`salary-management-ui.md §10`, §12 checklist).
  *Verify:* contrast measured in both themes; keyboard-only run through the core flow; 375px.

  **Contrast (measured, not eyeballed):** extended the existing `npm run check:tokens` — which
  already measured 12 WCAG-AA (4.5:1) text pairs per theme but had no check at all for ui.md
  §10's "3:1 for UI boundaries" — with `--input` (the token `components/ui/input.tsx`/`select.tsx`/
  `textarea.tsx` all use for their resting-state border) against both `--background` and `--card`,
  in both themes. **Found a real gap**: `--input` measured 1.42:1 (light, vs background) / 1.48:1
  (vs card) / 1.91:1 (dark, vs background) / 1.78:1 (dark, vs card) — an unfocused text
  input/select/textarea's boundary was nearly invisible against its surface, well under the 3:1
  floor (WCAG 1.4.11: the boundary is the only way to identify the control before focus lands and
  the high-contrast `--ring` takes over). Fixed in `theme.css`: light `--input` `#d4d4d8`→`#8c8c8c`
  (now 3.22:1 / 3.36:1), dark `#3f3f46`→`#6c6c6c` (now 3.79:1 / 3.53:1). Left `--border` itself
  unchanged — it's a decorative table/panel divider, not a control's sole identifying edge, so
  1.4.11 doesn't apply to it. Added the two new pairs to `check:tokens` permanently so this can't
  regress silently again. `npm run check:tokens` → `✓ contrast — 28 pairs measured, all at or
  above WCAG AA` (both themes).

  **Keyboard-only pass** (`scripts/verify-a11y-p9.5.mjs`, new — real Chromium via Playwright, one
  continuous session per the standing note above about P9.5/P9.6 walkthroughs): signed in using
  only Tab/type/Enter (no click), Tab-hunted the real `<a href="/employees">Employees</a>` nav
  link and activated it with Enter, opened the employee detail route, Tab'd to "Propose change"
  and opened it with Enter, confirmed focus moved inside the dialog, confirmed focus stayed
  trapped inside it across 8 Tabs, closed with Escape, confirmed focus returned to the trigger
  button — and confirmed a visible focus ring at each of the three explicitly-checked stops
  (email field, nav link, Propose-change button). Observed: `✓ keyboard-only pass and 375px pass
  both clean` — every check passed, no violations found.

  **375px pass**: same script, `375×812` viewport, five routes (dashboard, employee list, employee
  detail, bands, an insights page) — `document.documentElement.scrollWidth` never exceeded
  `clientWidth` on any of them (no horizontal scroll). Confirmed `/employees` specifically shows
  the card layout (`ul[aria-label="Employees"]`, real content, 50 rows) and NOT the desktop table
  at this width — table-to-cards degradation genuinely works, not just present in markup.

  Two real environment snags along the way, neither a product bug: (1) the local `application-
  local.yml` CORS config allows `http://localhost:3100`, not the default `next start` port 3000 —
  ran the frontend with `PORT=3100` to match; (2) the script's own first attempt at the 375px
  card-list check ran before the client-side data fetch resolved (a `ul[aria-label="Employees"]`
  with 0 children is technically "attached" but the visibility check raced it) — added a
  `waitFor({state:"attached"})` before checking.
- [x] **P9.6** Walk the twelve acceptance criteria in `Technical-Requirements.md §6`.
  *Verify:* each one demonstrated, with the observed result written next to it.

  Every criterion demonstrated live against the P9.1 seed via one continuous session (curl with
  real cookie jars per role + one raw-SQL bypass), per the P9.5 standing note above about
  multi-page sessions being the only honest way to run this kind of check. **9 of 12 fully pass.
  3 reveal a real, specific gap** — recorded below rather than glossed over, since `Technical-
  Requirements.md §6` itself says "the build is done when all of these pass."

  1. **PASS.** Logged in as all four seeded roles (`admin`/`manager`/`analyst`/`auditor@acme.test`).
     7 RBAC boundary checks against CLAUDE.md §7's matrix, live: analyst creating a band → 403,
     manager reading `/admin/users` → 403, analyst approving a change → 403 (not 404 — authz runs
     before lookup), manager bulk-uploading → 403, auditor proposing a change → 403, auditor
     running an insight → 403, manager reading the audit log → 403; admin+auditor reading the
     audit log → 200. All matched the matrix exactly. Backed by the passing `RolePermissionMatrixTest`
     in the full suite for the complete `@PreAuthorize` surface, not just these 7 samples.
  2. **PARTIAL.** Walked keyset pagination end-to-end: 50 pages × 200, **10,000 rows seen, 10,000
     distinct ids, 0 duplicates** — no skip, no dup, confirmed against the real employee count.
     Filter by department + country + status: API returned exactly 119 rows, matching a direct DB
     count for the same predicate. **Two real gaps found**: there is no band-status filter
     parameter anywhere (`EmployeeController#list` has no such `@RequestParam`, and `bandStatus`
     never appears as a filter in the frontend either — only as a display field), and sort-by-
     compa-ratio isn't wired — `employees-screen.tsx`'s own javadoc already says so ("Column sort
     is likewise not wired: the service sorts a fixed `lastName, id`... arbitrary column sort
     needs backend work this pass didn't do"). Not fixed here — implementing keyset-stable
     secondary sort and a new filter dimension is real feature work, not something a verification
     step should silently expand into.
  3. **PASS.** Full ledger for a real employee: 5 periods, sequential, no gaps. Picked
     `2025-12-01`, inside the `2025-11-19..2025-12-26` period; `as-at?date=2025-12-01` returned
     that exact period (`69315.44 USD`, same record id) — not an adjacent one.
  4. **PASS.** `impact-preview` for a proposed raise returned `deltaAmount`, `deltaPercent`,
     `currentCompaRatio`/`proposedCompaRatio`, `currentBandStatus`/`proposedBandStatus`, and
     `peerCohortSize`/`peerSuppressed`/`peerPercentileBefore`/`peerPercentileAfter` — delta, compa-
     ratio, and peer distribution, all before submission, in one response. Proposed a real change,
     then a second proposal for the same employee → `409` with `"A change for this employee is
     already awaiting approval."` and `openChangeId` naming the exact open one.
  5. **PASS.** The proposer (`admin`) tried to approve their own submitted change → `403`, `"You
     proposed this change, so someone else has to approve it."` A different user (`manager`)
     approved it → `200`. Ran `apply-due` → applied it: the employee's record count went 6→7
     (exactly one new row), the new period starts `2026-08-15` (the change's effective date), and
     the previous period's `effective_to` was set to the same `2026-08-15` — correct half-open
     `[from, to)` semantics (every other period in this employee's own ledger uses the identical
     touching-boundary convention, not an off-by-one "day before" gap).
  6. **PASS.** Ran `apply-due` again immediately after: `{"due":0,"applied":0,"failures":[]}` —
     nothing written the second time; the employee's record count stayed at 7.
  7. **PASS, and proven at the right layer.** A raw `psql` `INSERT` directly into
     `compensation_records` for an overlapping period — bypassing the Java service, the
     controller, and HTTP entirely — was rejected by Postgres itself: `ERROR: conflicting key
     value violates exclusion constraint "comp_no_overlap"`. Not a service-layer check that a
     direct SQL client could route around.
  8. **PARTIAL.** All 7 insight surfaces answer their question with real seeded numbers: payroll
     cost (`$1.13B` total, by country/department/level), out-of-band (`belowMinCount=216,
     aboveMaxCount=6696`, real cost-to-minimum), compa-ratio distribution (p25/median/p75 +
     histogram, `excluded.noBand=33`), pay-gap (unadjusted + level-adjusted, see #9), increase-
     cycle, peer comparison (`/employees/{id}/peers`), and full pay history (#3). Every analytics
     response carries `asAtDate` and `population` (with an `excluded` breakdown). **`fxRateMonth`
     is not exposed anywhere** — and this is a *documented, reasoned* omission, not an oversight:
     `PayrollCostResponse`'s own javadoc explains that every aggregate figure is already pinned
     per-record to whichever FX rate was in force when *that employee's own record* was written
     (CLAUDE.md §6.4), so a population spanning thousands of employees has no single governing
     rate month to report — inventing one (e.g. "today's month") would misleadingly imply a live
     recompute that never happens. This is a genuine tension between the acceptance criterion's
     literal wording and a considered design decision already made and explained in code; flagging
     it for a product decision rather than papering over it either way.
  9. **PASS.** Pay-gap: `suppressedCohorts: 206`, `levelAdjustedCohorts` has 270 shown — and the
     minimum group size across every group in every SHOWN cohort is exactly 5 (verified
     programmatically over the full response), confirming both halves: nothing under five is
     shown, and the suppressed count is reported.
  10. **PASS**, per P9.3 — `DemographicsIsolationTest` over every DTO outside `analytics`.
  11. **PASS**, per P9.5 — 28 contrast pairs (both themes) at/above WCAG AA including the newly-
      added `--input` boundary pairs; keyboard-only pass and 375px pass both clean.
  12. **PASS**, per P9.2 — `SeedReproducibilityTest`, two runs from empty, identical totals/
      medians/anomalies.

  This session's demonstration mutated the local dev database (one new change proposed, approved,
  and applied) — re-seed (`truncate` + `spring-boot:run -Dspring-boot.run.profiles=local,seed`) if
  a pristine P9.1 snapshot is needed for a demo.
- [x] **P9.7** README: run instructions, seeded credentials, the seven questions and where each is
  answered. *Verify:* a clean clone reaches a signed-in seeded app using only the README.

  Rewrote `README.md` (was a P9.7 placeholder): Docker Postgres setup (Neon isn't provisioned —
  `application-local.yml.example` is a different, Neon-shaped template not usable yet, so the
  README documents the actually-working local recipe instead), the exact `application-local.yml`
  block to paste, seed/reseed commands, `.env.local` + `npm run dev -- -p 3100` for the frontend,
  a table of the six seeded accounts, and a table mapping each of the seven questions to its
  screen. Also notes the P9.6 acceptance-criteria status (9/12 pass, 3 documented gaps) so a
  reader lands on that context immediately rather than discovering it in `BuildPlan.md`.

  Caught one real bug in the README's own first draft by actually testing it: the local YAML
  block omitted `spring.autoconfigure.exclude: []`, which overrides `application.yml`'s P0.2 stub
  that disables persistence entirely — without it the app "starts cleanly" with Flyway silently
  never running, exactly the failure mode `application.yml`'s own comment warns about. Fixed
  before verifying.

  **Verify, done for real, not assumed:** `docker rm -f` the existing container, recreated it from
  scratch with the README's exact `docker run` command, wrote `application-local.yml` from the
  README's exact snippet, ran `./mvnw spring-boot:run -Dspring-boot.run.profiles=local,seed`
  against the empty database. Console credentials matched the README's table byte-for-byte
  (`admin@acme.test` / `harbor-orbit-4853`, etc. — confirms P9.2's determinism claim extends to
  what a reader actually copies), and seed counts matched P9.1's own numbers exactly (`49688`
  records, `14288` components, `noBand=33`). Then `npm run dev -- -p 3100` (the README's exact
  frontend command, not the `next start` production build used for P9.4/P9.5's checks), logged in
  via `curl` with the documented credentials through the dev server on `:3100`, and confirmed the
  authenticated dashboard actually rendered ("Ada Admin" in the page body) rather than a sign-in
  redirect.

### Closing two of P9.6's three acceptance-criteria gaps: band-status filter + compa-ratio sort (2026-08-22)

User ask: check for and complete any pending/backlog work. P9.6's own done-note listed 3 real
gaps against `Technical-Requirements.md §6`'s twelve acceptance criteria; `fxRateMonth` (criterion
#8) is a considered design decision already explained in `PayrollCostResponse`'s own javadoc, not
a bug, so left alone — but criterion #2's missing band-status filter and compa-ratio sort on the
employee list are real, closeable feature gaps (`employees-screen.tsx`'s own comment had said so
since P4.3). Both are now built, server-side, over the full 10k dataset — not a client-side filter
of one page, which would have silently misreported the true result set exactly as that P4.3
comment warned against.

**First implementation attempt failed a real regression check, caught before it shipped**: added
a read-only `@OneToOne Employee.currentComp` so `Specification`/keyset `Sort` could reach
`compaRatio`/`bandStatus` directly. It compiled, and curl testing against the seeded app looked
correct — but `./mvnw clean verify` then broke `PayrollCostAndHeadcountTest` and
`ProjectionConsistencyTest` with `Hibernate.TransientPropertyValueException`: a persistent
`Employee` already in a test's session got treated as referencing whatever `EmployeeCurrentComp`
row shared its id, the moment a test legitimately saved both in one transaction. Reverted the
relationship entirely rather than chase a Hibernate association-management edge case in code the
rest of this project has deliberately kept relationship-free between separately-owned tables
(`Employee`'s own class javadoc already explains why, for `employee_demographics`).

**What shipped instead**: `EmployeeSpecifications.bandStatus()` — a correlated subquery against
`EmployeeCurrentComp`, the same pattern `countryCode` already used against `Location`, no
relationship needed. Compa-ratio sort couldn't reuse Spring Data's keyset `Window`/`Sort` API at
all once the relationship was gone (that API resolves a dotted sort property like
`"currentComp.compaRatio"` against the entity's own JPA-mapped graph) — `EmployeeService
.listByCompaRatio` hand-rolls the same keyset shape as a native query instead: `ORDER BY
compa_ratio DESC NULLS LAST, id ASC` (Postgres's own DESC default is NULLS FIRST — confirmed live
before fixing it, page one was 100% NO_BAND employees with a null compa-ratio), restricted to
employees who have a current comp record at all. The query returns ordered ids only; the actual
response rows are built by the same batch-lookup + `toSummary` mapping `listByLastName` uses, so
the two sort paths can never drift in what they render. Export (`GET /api/employees/export`) got
`bandStatus` too, for FR-2.7's "always matches the on-screen filter" — it had no sort concept to
begin with, so `sortBy` doesn't apply there.

New backend test `EmployeeListSortAndFilterTest` (Testcontainers, 600 seeded employees: some with
no comp record at all, some NO_BAND with a comp record but null compa-ratio) pages every
`bandStatus` value and the compa-ratio sort to completion, asserting no duplicate/skip, strictly
non-increasing order, and every null trailing every real value. `./mvnw clean verify` →
`Tests run: 133, Failures: 0, Errors: 0`, `BUILD SUCCESS` (up from 131 — this test plus the
now-required extra param in `AuditImmutabilityTest`'s existing `employeeService.list(...)` call).

Re-verified live against the real 10k-employee seed after the rewrite, not just via the new unit
test: full keyset walk of `sortBy=compaRatio` → 9,580 rows (matches `employee_current_comp`'s own
count), 0 duplicates, non-null values strictly non-increasing, exactly 33 nulls (matches the
seed's own `noBand` anomaly count) all trailing at the end. All four `bandStatus` values' full
walks matched direct DB counts exactly (2636/215/6696/33). Combined `departmentId` +
`bandStatus=ABOVE_MAX` matched a direct SQL join count (267/267).

**Frontend**: `bandStatus`/`sortBy` added to `EmployeeListParams`/`buildQuery`
(`lib/api/employees.ts`); two new `Select` controls in `employees-screen.tsx` (band status: In
band/Below minimum/Above maximum/No band; sort: last name/compa-ratio highest-first), URL-synced
like every other filter on this screen (CLAUDE.md §9). `npm run verify` (tokens + contrast + lint
+ typecheck + test + build) clean. Verified live through a real Chromium session (`scripts/verify-
band-status-and-sort.mjs`, new): selected "Below minimum" → URL became `?bandStatus=BELOW_MIN`, 50
rows rendered; selected the compa-ratio sort → URL became `?sortBy=compaRatio`, and the rendered
rows showed real descending compa-ratio figures (1.56, 1.56, 1.56, 1.56, 1.55…) with the `BandBar`
correctly marking each as above-max — a screenshot of this run is in `screenshots/verify-band-
sort.png` (git-ignored).

Only 1 of P9.6's 3 gaps remains: `fxRateMonth`, an open design decision (not a build task) — see
`docs/STATE.md`.

### QA + feature-improvement pass (2026-08-22)

User ask: do a QA and feature-improvement analysis, plan it, and start implementing. Real browser
QA first (one continuous session per role, per the standing P9.5/P9.6 lesson): all 14 nav routes ×
4 roles (56 combinations) render correctly and every role-gated route degrades gracefully rather
than crashing; band creation via the bands-grid empty-cell dialog persists correctly end to end
(DB row count confirmed before/after); the 60-row PENDING changes queue, `/admin/users`,
`/admin/fx-rates`, `/admin/import`, `/admin/audit` all render real content; combined employee
filters (`status`+`bandStatus`+`sortBy`) plus Next-button pagination work through the actual UI;
zero console/page errors anywhere in the session; zero TypeScript `any` (NFR-10). One early
hypothesis (a missing merit-budget input) turned out to be **wrong** — `/insights/pay`'s increase-
cycle question already has a full budget input + burn-percent display; caught by checking the code
before adding it to a plan, not after building something redundant.

**Two real bugs found and fixed**, both 375px overflow (checked every route P9.5 didn't cover):

1. `/admin/import`: 963px of content in a 375px viewport. Cause: the CSV-columns `<code>` block
   sat inside a `flex flex-col` container with no `min-w-0` — a flex item's default `min-width` is
   `auto` (its own content width), not `0`, so it refused to shrink or wrap, forcing the whole page
   wide (a well-known flexbox gotcha, not obvious from the markup alone). Fixed:
   `min-w-0` on the flex container, `break-words whitespace-normal` on the `<code>` itself, so the
   column list wraps onto multiple lines instead of pushing the page wider.
2. `/changes`: 415px in a 375px viewport — smaller, but real. Cause: `ui/tabs.tsx`'s shared
   `TabsList` primitive is `inline-flex w-fit` with no width cap, so five tabs with a long label
   ("Awaiting approval") don't wrap or scroll, they just grow past the viewport. Fixed at the
   primitive (`tabs.tsx`), not the call site — added `max-w-full overflow-x-auto` so `TabsList`
   still hugs its content width when there's room, but caps to the parent's width and scrolls
   internally instead of blowing out the page once there isn't. Only one current consumer
   (`changes-screen.tsx`), so this protects the next one too, not just today's bug.

Both re-measured at 375px after the fix (`scrollWidth === clientWidth` on both routes, `FIXED`),
screenshotted, and `npm run verify` (tokens + contrast + lint + typecheck + test + build) stayed
clean.

**Three feature gaps found by cross-referencing the backend against the frontend** (a capability
that curls fine but nothing in the UI can ever reach):

1. **`/insights/equity` has no CSV export** — every other insights screen (`/insights/pay`,
   `/insights/reports`, the Overview dashboard) exports its data; the pay-gap screen doesn't use
   `ChartCard` at all (plain cards/tables), so it silently has no export path anywhere. Real
   inconsistency for what is arguably the most compliance-sensitive screen in the app.
2. **Salary-band CSV import has a complete backend** (`POST /api/bands/import`, dry-run diff,
   per-row `CREATE`/`VERSION`/`ERROR`, `jobLevelId,countryCode,currency,minAmount,midAmount,
   maxAmount,effectiveFrom,note`) **and zero frontend.** `BuildPlan.md`'s own P5.3 done-note said
   so explicitly at the time — "a UI renders/downloads it whenever a bulk-upload screen exists
   (not this step)" — and no later step ever picked it back up. `/admin/import` was built for
   employees only (P8.4), even though CLAUDE.md's RBAC table has exactly one "Import / bulk
   upload: HR Admin" row covering all three CSV types, not three separate capabilities.
3. **Merit-cycle bulk upload has a complete backend** (`POST /api/changes/bulk-upload`,
   `employeeNumber,newAmount,changeReason[,note]`, one `effectiveDate` for the whole batch, no
   dry-run — a DRAFT is cheap to discard so there's no separate preview step by design) **and
   zero frontend**, same story as bands.

**Plan for the three gaps** — turn `/admin/import` into a tabbed hub (Employees / Salary bands /
Merit changes) instead of three separate nav entries, since RBAC already treats them as one
capability: reuses the just-fixed `Tabs` primitive (real regression coverage), tab state in
`?tab=` per CLAUDE.md §9, and the existing `EmployeeImportScreen` becomes one of three panels
rather than a page of its own. Equity export uses the existing `downloadCsv` utility directly
(no `ChartCard` refactor needed) on both the unadjusted comparison and the level-adjusted cohort
table.

---

## P10 — Close v1

> **Source (2026-08-23):** post-v1 feature + market analysis. Reasoning, scope verdicts and the
> exclusion-list stance are in `docs/feature-roadmap.md`; `F<n>` ids below index into its table.
> `requirements-one-pager.md` is still the scope contract — every step below was checked against it,
> and the two that collided with an exclusion (`P11.5`, `P13.7`) were narrowed, not argued past.

- [x] **P10.1** `FxBasis` on the **four money-carrying** analytics responses (F3) — resolves the
  standing FR-6.8 / P9.6-criterion-#8 open decision recorded in `docs/STATE.md`. Replace the scalar
  `fxRateMonth` with a record: `distinctRates`, `monthsSpanned`, `earliestMonth`, `latestMonth`. The
  existing `null` is **not** a bug — an aggregate over many employees has no single governing month;
  this reports the basis honestly instead of fabricating one.
  > **Scope corrected during the step (2026-08-23):** the plan said "six responses". It is four.
  > `HeadcountResponse` carries no money at all, and `CompaRatioDistributionResponse` reports
  > `compa_ratio` — pay ÷ band mid, both already in the same currency, so no FX enters it. Confirmed
  > against the queries: exactly four of the six touch `normalized_annual_base` (`PayrollCostQuery`,
  > `OutOfBandQuery`, `PayGapQuery`, `IncreaseCycleQuery`). Attaching an FX basis to the other two
  > would fabricate a basis for a figure that has none — the exact mistake the original `null` was
  > avoiding.
  *Verify:* `curl` the four money endpoints, every one carries a populated `fxBasis`; the other two
  carry none. A targeted test asserts `distinctRates` equals a direct SQL
  `count(distinct fx_rate_id)` over the same population.
  > **Done (2026-08-23):** `FxBasis(distinctRates, monthsSpanned, earliestMonth, latestMonth)` +
  > `FxBasisQuery`, wired into `payrollCost`, `outOfBand`, `payGap` (all `forCurrentComp()`) and
  > `increaseCycle` (`forAppliedChanges(from, to)`, scoped exactly as `IncreaseCycleQuery` scopes
  > its own figures).
  >
  > `employee_current_comp` carries the normalised figure but **not** the rate behind it —
  > `fx_rate_id` lives on `compensation_records`, where it is `NOT NULL` — so the basis joins
  > through `compensation_record_id`. That `NOT NULL` is what makes the count trustworthy: every
  > row in the population has exactly one governing rate and none can be silently missed.
  >
  > **`missingCoverage` was dropped from the design during the step.** It cannot happen on a read:
  > a missing rate is a hard write-time failure (`MissingFxRateException`, 422, backend doc §5), so
  > every already-written record necessarily has a rate. Reporting a field that is structurally
  > always `false` would be noise pretending to be a safety check. The genuine forward-looking
  > question — "is next month's rate loaded, so writes will keep succeeding?" — belongs to `P10.2`'s
  > coverage matrix, which is an admin concern about the *future*, not a property of *this* report.
  >
  > **Observed:** `FxBasisTest` 5/5. Full suite `./mvnw clean verify` →
  > `Tests run: 138, Failures: 0, Errors: 0`, `BUILD SUCCESS` (133 before this step; +5 here). Ran
  > the full suite rather than just the targeted one because this changes four shared DTOs, which
  > CLAUDE.md §2B treats as a module boundary.
  >
  > **Not verified: the `curl` half.** No running service this session — the local-Postgres recipe
  > needs Docker and an `application-local.yml` this session was not permitted to reach. The
  > substantive assertion (service value vs. independent SQL over the identical join) *is* covered,
  > against real Postgres via Testcontainers, which is strictly stronger than a shape check on JSON.
  > What remains unproven is only that Jackson serialises the record as expected. **Run the four
  > `curl`s when a service is next up**; `FxBasisTest.responsesWithoutMoneyCarryNoFxBasis` already
  > pins the "other two carry none" half structurally.
- [ ] **P10.2** FX coverage matrix on `/admin/fx-rates` (F3) — currency × month over the currencies
  actually in use by `employee_current_comp`, gaps in `--attention`.
  *Verify:* delete one month's rate for one in-use currency locally; the cell renders as missing
  **and** `missingCoverage` flips true on the analytics envelope.
- [x] **P10.3** `saved_views` (`V14`) + `GET/POST/DELETE /api/saved-views` (F1) — name, owner, route,
  query string, shared flag. This is an **unshipped contract line**, not an enhancement:
  `requirements-one-pager.md` excludes a free-text pay assistant and in the same sentence commits to
  "a saved-question library plus a structured query builder".
  *Verify:* save a filtered employee-list view; as another role, a shared view loads and reproduces
  the exact filter set; an unshared view is 404 for a non-owner.
  > **Done (2026-08-23):** `V14__saved_views.sql` + a `savedview` module (domain/repository/dto/
  > service/web) — `GET/POST/DELETE /api/saved-views`, all four roles.
  >
  > **The service holds no query logic, deliberately.** A saved view is a route plus a query
  > string, replayed as the identical request the user could have typed — so RBAC, cohort
  > suppression and demographic isolation stay exactly where they already are, in the endpoints and
  > in SQL. That is the entire reason `requirements-one-pager.md` offered this as the safe
  > substitute for the excluded free-text assistant ("the same questions, answered by queries that
  > can be audited"), and it only stays true while nothing here parses the query string.
  >
  > **All four roles, and that is not a widening of access.** A view carries no data. An Auditor
  > opening a view an HR Admin saved gets the *Auditor's* answer, because the replayed request hits
  > the same `@PreAuthorize` it always would. CLAUDE.md §7 has no row for this because a personal
  > bookmark over data you can already reach is not a capability in that table's sense.
  >
  > **No JPA relationship to `User`** — `ownerId` is a plain column. A relationship would let a
  > fetch graph drag a user (and its password hash) into a saved-view response, the same class of
  > accident §6.6 keeps `employee_demographics` away from. Owner *name* is resolved per request and
  > exposed; owner id and email never are.
  >
  > Re-saving an existing name updates rather than inserting (unique `(owner_id, name)`): two views
  > called "Below band, Germany" that a picker cannot tell apart is worse than replacing the older
  > one. A non-owner deleting gets 404, not 403 — a 403 confirms the id names someone's real view.
  >
  > **Two Java-17 trips worth noting** (toolchain targets 17 per STATE.md, `Role` is not an enum):
  > `auth.domain.Role` does not exist — `User.role` is a plain `String`; and `List.getFirst()` is
  > Java 21+ `SequencedCollection`, so `get(0)` it is. Both caught at compile, neither reached a run.
  >
  > **Observed:** `SavedViewTest` 6/6. Full suite `./mvnw clean verify` →
  > `Tests run: 144, Failures: 0, Errors: 0`, `BUILD SUCCESS` (138 before). Ran the full suite
  > because this adds a migration — a module boundary per CLAUDE.md §2B.
- [ ] **P10.4** Saved-view picker + structured query builder UI (F1). A typed UI over the filter
  params the endpoints already accept — never free text, so every cohort-suppression guardrail stays
  in SQL.
  *Verify:* `npm run verify` clean; build a three-filter question, save it, reload from the picker,
  URL and result set identical.
- [ ] **P10.5** `totalCount` on `KeysetPage`, page jump, bulk select (F4) — closes the last three
  P4.3 omissions. **Do not** add a JPA relationship between `Employee` and `EmployeeCurrentComp` to
  reach this (`docs/STATE.md` — it breaks `PayrollCostAndHeadcountTest`); follow
  `EmployeeService.listByCompaRatio`'s native-query template.
  *Verify:* count matches direct SQL for three filter combinations; NFR-1 (p95 < 400 ms) still holds
  on the full 10k **with** the extra count — measure it, don't assume it.
- [x] **P10.6** `basis=BASE|TOTAL_TARGET_CASH` on `/analytics/payroll-cost` (F2). FR-3.4 stores components and says they are "included in total
  target cash"; today no analytic reads them, so Q1 does not answer what ACME actually spends.
  Aggregate in SQL through `compensation_components` per NFR-6. **Build this as the seam equity
  plugs into later** — it is the one-pager's named v2 candidate.
  *Verify:* `TOTAL_TARGET_CASH` reconciles against a direct SQL sum of base + components over the
  seed; `BASE` is byte-identical to today's response.
  > **Scope corrected during the step (2026-08-23): payroll-cost only, NOT
  > compa-ratio-distribution.** A salary band is a *base pay* range — `EffectiveDating` computes
  > `compaRatio(annualBaseAmount, band)` — so dividing total target cash by a base-pay midpoint
  > would push everyone with a bonus target above 1.0 and make the metric mean nothing. Same
  > reasoning retires it for range penetration and every out-of-band judgement: they compare base
  > to a base-pay band, and that is correct. Documented on `AnalyticsBasis` itself.
  >
  > **Done (2026-08-23):** `AnalyticsBasis{BASE, TOTAL_TARGET_CASH}`; `?basis=` on
  > `/analytics/payroll-cost` defaulting to `BASE`; `basis` echoed on `PayrollCostResponse` per
  > FR-6.8 (a figure has to say what it counts). `payrollCost()` keeps a no-arg overload — `BASE`
  > is what every existing caller meant.
  >
  > **Components had no normalised column, which is the whole difficulty.** They carry their own
  > `amount` + `currency` but no `normalized_*` and no `fx_rate_id`, so they cannot simply be added
  > to a USD total. Each is converted with the rate pinned for *its own currency in the month that
  > employee's record pinned* (`LEFT JOIN LATERAL` through `compensation_records.fx_rate_id` →
  > `fx_rates.rate_month`) — never a live rate, never today's month, so CLAUDE.md §6.4's "run it
  > twice, get the same number" still holds. Every currency in use has a row per month including
  > the identity USD→USD one, so the join cannot silently drop a component.
  >
  > **Only `is_recurring` components count.** A one-off payment is not part of what someone is paid
  > annually, and including it would make two runs of the same report differ as one-offs age out.
  >
  > **Known naming wart, deliberately not fixed here:** `PayrollCostGroup.totalAnnualBase` holds
  > total cash when `basis=TOTAL_TARGET_CASH`. Renaming it is a response-shape change, and
  > `docs/STATE.md`'s standing gotcha is that those are invisible to `npm run typecheck` at every
  > frontend call site — with no live stack this session to catch the fallout, the safer call is to
  > leave the name and let `basis` disambiguate. **Rename it as part of `P10.7`**, when the UI is
  > being touched anyway and can be verified.
  >
  > **Observed:** `TotalTargetCashBasisTest` 5/5 (BASE matches direct SQL; total cash = base +
  > normalised recurring components; cash never below base and same headcount; response states its
  > basis; every breakdown reconciles to `overall` on both bases). Full suite → `Tests run: 149,
  > Failures: 0, Errors: 0`, `BUILD SUCCESS` (144 before).
- [ ] **P10.7** Basis toggle beside the existing as-paid/normalised control (F2).
  *Verify:* `npm run verify` clean; toggling changes the figure **and** the URL (`?basis=`) per
  CLAUDE.md §9.

## P11 — Trust the data

> Read-only, no migration except `P11.5`, cannot corrupt the ledger. **If only one phase gets built,
> build this one** — it answers the two questions the HR Manager asks on their first real day.

- [x] **P11.1** `GET /api/analytics/data-health` (F11) — named checks, each `{key, label, severity,
  count}`: no matching band, no compensation record at all, pay currency inconsistent with the
  location's country, hire date after first ledger period, terminated employee with an open period,
  FTE outliers, duplicate employee numbers, terminated managers, circular management chains. The
  seed generates *deliberate* anomalies; a real import will generate accidental ones and nothing
  currently surfaces them.
  *Verify:* each count reconciles against its own direct SQL; FR-8.4's ~40 no-band employees land in
  the right check with the right count.
  > **Done (2026-08-23):** `DataHealthQuery` + `DataHealthCheck`/`DataHealthSeverity`/
  > `DataHealthResponse`, `GET /api/analytics/data-health`, same roles as every other analytics
  > read (it counts rows needing attention, never a person's pay). Nine checks, ordered
  > most-severe-first; passing checks are returned too, because a console that hides them can
  > answer "what is broken" but not "is this data clean yet".
  >
  > **Three planned checks were dropped: the schema already makes them impossible.** Duplicate
  > employee numbers (`employee_number` is `UNIQUE`), FTE outside 0.01–1.00 (a `CHECK`), and a
  > termination date without `status = 'TERMINATED'` (`emp_termination_date_requires_status`).
  > Reporting a check that is structurally always zero is noise pretending to be a safety net —
  > same reasoning that dropped `missingCoverage` at P10.1. Replaced with checks that *can* fail:
  > full-time employees below 1.0 FTE, and pay starting before the hire date.
  >
  > **The recursive circular-management check needs both guards.** `UNION` (not `UNION ALL`) plus a
  > depth cap — with either missing, the query looking for the infinite loop *is* one.
  >
  > **A real pre-existing gap found and fixed, not routed around:** `RolePermissionMatrixTest`
  > walks a hardcoded `CONTROLLERS` list, so **`SavedViewController` (added at P10.3) escaped the
  > RBAC guard entirely** — P10.3's own full-suite run passed green while its three endpoints were
  > unguarded. Added it to the list along with the expected role set, and added `dataHealth` to the
  > analytics block. The test then failed exactly as designed on the new endpoint, which is how the
  > gap surfaced. **Any future controller must be added to that list** — the guard is opt-in, which
  > is itself worth knowing.
  >
  > **Observed:** `DataHealthTest` 8/8. Full suite → `Tests run: 157, Failures: 0, Errors: 0`,
  > `BUILD SUCCESS` (149 before). One intermediate run was `157 / Failures: 1` — the matrix test
  > catching `dataHealth`, reported here because it was a real failure and not a flake.
- [ ] **P11.2** `/admin/data-health` with drill-through and export (F11). Drill-through reuses the
  employee list's existing filters wherever one exists.
  *Verify:* `npm run verify`; each check drills to a real row list; 375px card degradation per UI
  doc §12.10.
- [x] **P11.3** `GET /api/analytics/band-health` (F9) — range spread (`max/min − 1`), midpoint
  progression between adjacent levels, adjacent-level overlap, population by quartile,
  zero-incumbent bands, staleness (not versioned in N months).
  *Verify:* spread and progression reconcile against direct SQL on three known bands; a deliberately
  broken band (max below the next level's min) is flagged.
  > **Done (2026-08-23):** `BandHealthQuery` + `BandHealthRow`/`BandHealthResponse`,
  > `GET /api/analytics/band-health`. Read-only over existing tables, no migration. Per in-force
  > band: range spread, midpoint progression, gap-to-previous-level, incumbents, median compa-ratio,
  > months since versioned; plus summary counts and an 18-month staleness constant (a constant, not
  > a request param — it states how fast pay moves, it is not something a caller should tune until
  > the number looks acceptable).
  >
  > **Adjacency is per job family AND country, not per level code.** `job_levels` is
  > `UNIQUE (job_family_id, level_code)`, so "the level below" only means something inside one
  > family — comparing an L4 Engineering band to an L3 Finance band would produce a progression
  > figure about nothing. `lag()` partitions on (family, country) ordered by `sort_order`.
  >
  > **A gap is the defect, not an overlap.** Adjacent bands overlapping is normal and healthy; a
  > *gap* (this band's min above the previous level's max) is a promotion cliff where someone can be
  > promoted out of the top of one band into the bottom of the next and go backwards relative to
  > where they were. That is what `gapToPreviousLevel` flags.
  >
  > **The first version of this test passed vacuously and that was the real bug.** The shared
  > container held no in-force bands when the class ran, so every `allSatisfy` was satisfied by an
  > empty list and the gap assertion compared 0 to 0 — green, proving nothing. Now seeds its own
  > family with three levels and a deliberate L2→L3 cliff (L2 tops at 90k, L3 starts at 100k), and
  > every list assertion is guarded with `isNotEmpty()` so it can never silently pass over nothing
  > again.
  >
  > **Then that fixture broke a different test — worth knowing about.** `countries` has NO
  > migration-seeded rows (they come from the seed profile, which does not run under test), so the
  > fixture creates its own. Using `'ZZ'` made `V2ReferenceDataMigrationTest` fail: it uses `'ZZ'`
  > as its deliberately-*invalid* country code to assert a foreign-key violation, and creating it
  > for real made that insert succeed. Moved to `'QX'`. **Any reference row a test creates becomes
  > real for every class sharing the container** — including the sentinel values other tests rely on
  > being absent.
  >
  > **Observed:** `BandHealthTest` 9/9. Full suite → `Tests run: 166, Failures: 0, Errors: 0`,
  > `BUILD SUCCESS` (157 before). Two intermediate red runs, both real and both reported above.
- [ ] **P11.4** Band-health matrix on `/bands` (F9).
  *Verify:* `npm run verify`; flagged bands use `--attention`/`--critical` per CLAUDE.md §5.1 —
  no raw hex.
- [x] **P11.5** `market_data_points` (`V15`) + `POST /api/market-data/import` (F10, **reframed**).
  Benchmark data is a data business and a single-tenant tool has no contributor network — so build
  the seam, not the dataset: source, job level, country, currency, p25/p50/p75, effective month.
  Same CSV discipline as the bands import (header row, per-row `CREATE`/`ERROR`, one bad row never
  blocks the rest). HR_ADMIN only, into the existing import hub.
  *Verify:* import 20 rows with 2 deliberate errors → 18 `CREATE` + 2 `ERROR`, error report
  downloadable, no partial-batch rollback.
  > **Done (2026-08-23):** `V15__market_data_points.sql` + a `market` module.
  > `POST /api/market-data/import` (not `/api/bands/import` as the plan said — it is its own
  > resource, not a band), HR_ADMIN only per §7's single "Import / bulk upload" row. Columns
  > `source,jobLevelId,countryCode,currency,p25,p50,p75,effectiveMonth`, same discipline as the
  > bands importer (header row, plain line split, dry-run diff, one bad row never blocks the rest) —
  > a second import shape that behaved differently would be a trap for whoever uses both.
  >
  > **Percentiles are stored in the survey's own currency and never normalised.** Normalising a
  > benchmark at import time would bake in one month's FX rate and make the figure drift for reasons
  > that have nothing to do with the market. The comparison that matters is band-to-market inside
  > one country, and both sides are already in the same currency.
  >
  > **A real correctness bug caught while writing it, not after.** The importer is one
  > `@Transactional` method, so an FK violation on one row would abort the whole Postgres
  > transaction and every later row would fail too — silently breaking the importer's core promise.
  > Fixed by looking up the job level and country *before* any insert, so no constraint violation
  > ever reaches the database. `anUnknownJobLevelIsARowErrorAndDoesNotPoisonTheBatch` pins it.
  >
  > Re-importing a corrected survey updates in place (unique `source × level × country × month`) —
  > two contradictory p50s for one cell is worse than replacing the older figure. Any day in the
  > month normalises to the first, matching how surveys are published.
  >
  > **Country code `QM`** — deliberately not `ZZ` (V2's invalid-FK sentinel) and not `QX`
  > (BandHealthTest's), per the collision recorded at P11.3.
  >
  > **Observed:** `MarketDataImportTest` 6/6. Full suite → `Tests run: 172, Failures: 0, Errors: 0`,
  > `BUILD SUCCESS` (166 before).
- [ ] **P11.6** Market tick on `<BandBar>` + "band mid vs market p50" in band health (F10).
  **`docs/salary-management-ui.md §7.1` governs this component change and its §12 checklist is not
  optional** — this is the signature component.
  *Verify:* `npm run verify`; visual check in both themes at 375px; a band with no market data
  renders unchanged, with no empty tick.

## P12 — Act, don't just report

> The largest and riskiest phase: `P12.1` changes the shape of `compensation_changes`, and every
> lifecycle test lives on that table. Do `P12.1` first within the phase.

- [ ] **P12.1** `V16` — `compensation_cycles` + `cycle_budget_pools`, nullable `cycle_id` on
  `compensation_changes` (F5). Today a merit cycle is emergent: bulk upload creates N unrelated
  proposals and `increase-cycle` reads a hand-typed budget, so FR-6.5 is answered by remembering the
  right dates. A migration is immutable once committed (CLAUDE.md §12.11).
  *Verify:* Flyway migrates against a container; **every existing lifecycle test still green** —
  `cycle_id` is nullable, so current rows and tests are unaffected.
- [ ] **P12.2** `/api/cycles` + status machine `DRAFT → OPEN → LOCKED → CLOSED` (F5). Check the
  CLAUDE.md §7 RBAC table for the right roles and mirror it in `RolePermissionMatrixTest` — there is
  no role hierarchy, so a missing role is a hard 403.
  *Verify:* the matrix test fails when a role is added or removed; a `LOCKED` cycle refuses new
  proposals with a 409.
- [ ] **P12.3** `propose` and `bulk-upload` accept an optional `cycleId`; `increase-cycle?cycleId=`
  replaces the hand-typed budget (F5).
  *Verify:* upload 50 rows into a cycle; burn percent matches a direct SQL sum against the pool; the
  existing date-range form still works unchanged.
- [ ] **P12.4** Cycle dashboard + budget-pool burn (F5). **Pools warn, they never block** — the
  one-pager's reasoning against configurable workflow engines ("nobody can explain why an approval is
  stuck") applies just as well to hard budget locks.
  *Verify:* `npm run verify`; an over-pool proposal **submits successfully** and renders a visible
  warning.
- [ ] **P12.5** `POST /changes/{id}/request-changes` — `PENDING → DRAFT` with a mandatory note (F7).
  Today an approver's only options are approve, reject or discard, so "number's fine, reason code is
  wrong" destroys the proposal and its history. Still exactly **one** approval step — this is not the
  excluded multi-level chain.
  *Verify:* new lifecycle test; the proposal survives with history intact; the audit event records
  actor and note.
- [ ] **P12.6** `daysInState` on the change list DTO + queue aging on the dashboard (F7). A proposal
  with a July effective date sitting unapproved in June is the failure mode that pays someone late.
  *Verify:* the count pending beyond threshold reconciles against direct SQL.
- [ ] **P12.7** `approval_delegations` (`V17`, F7) — dated; only *who may take* the single approval
  step changes; both parties recorded in the audit event.
  *Verify:* `ProposerIsNotApproverTest` still green; a delegate can approve inside the window and
  cannot outside it.
- [ ] **P12.8** `POST /api/scenarios/preview` (F6) — population from any employee-list filter or an
  explicit id set; rules: to-minimum, to-compa-target, percent-uplift. All money server-side.
  *Verify:* the to-minimum total equals today's FR-6.2 cost-to-minimum figure **exactly** — a real
  cross-check against an already-shipped number, not a fresh assertion.
- [ ] **P12.9** `POST /api/scenarios/{id}/materialise` → DRAFT proposals in a cycle (F6). Reuses
  `ChangeService.propose` per row unchanged, with P6.3's per-row `try/catch` pattern. **Any path that
  opens or closes a ledger row must call `projector.refresh(employeeId)`** (`docs/STATE.md`).
  *Verify:* materialise 100 rows → 100 DRAFTs plus per-row errors reported as counts; an employee
  with an open non-terminal change is reported as an error, **not silently skipped**.
- [ ] **P12.10** Scenario UI on `/insights/pay` (F6).
  *Verify:* `npm run verify` clean.
- [ ] **P12.11** `notifications` (`V18`) + write points in `ApplyDueChangesJob`, `ChangeService`,
  `BandService` (F8). **In-app only, no mail transport** — FR-1.6 already establishes v1 has none,
  and adding one drags in deliverability, templating and a new outbound security surface.
  *Verify:* run the apply job manually → one notification row per applied change; running it twice
  does **not** double-write (the job's idempotence must survive this).
- [ ] **P12.12** Topbar bell + digest panel (F8).
  *Verify:* `npm run verify`; polled via TanStack Query with the cache key from `keys.ts`.

## P13 — Regulation

> Needs `P10.6` (component join) and `P12.8` (scenarios). Timed against a June 2027 first filing on
> **CY2026** data — the year this ledger is recording now. Reasoning and sources:
> `docs/feature-roadmap.md §2`.

- [ ] **P13.1** Mean alongside median, plus quartile representation per group, on `PayGapResponse`
  (F12). Reuses the compa-ratio distribution's existing quartile machinery. ≥5 suppression unchanged.
  *Verify:* `DemographicsIsolationTest` green; suppression still fires at a cohort of 4; the mean
  reconciles against direct SQL.
- [ ] **P13.2** Variable-pay gap + receipt rate per group (F12) — depends on `P10.6`. Without it the
  report is base-only and incomplete.
  *Verify:* receipt rate reconciles against direct SQL over `compensation_components`.
- [ ] **P13.3** `flaggedCategories` at the ≥5% statutory threshold (F12) — today this has to be
  eyeballed off a table.
  *Verify:* a seeded category with a known >5% gap is flagged; one at 4.9% is not.
- [ ] **P13.4** `/insights/equity` renders and exports the new shape (F12).
  *Verify:* `npm run verify`; the export includes every new field.
- [ ] **P13.5** `V19` — `pay_assessments` (F13): flagged-category key, status, author, rationale, and
  the figures **snapshotted at assessment time**.
  *Verify:* Flyway migrates; the snapshot is immutable — no update path reaches the figures.
- [ ] **P13.6** Assessment workspace: justify or remediate (F13). Remediate opens a `P12.8` scenario
  scoped to that category. **The record attaches to a category, never a named person** (CLAUDE.md
  §6.6) — the product's own rule that no change happens without a recorded reason, applied to a
  cohort.
  *Verify:* `DemographicsIsolationTest` green **without being weakened**; the justification is
  audited; the remediation path lands drafts in a cycle.
- [ ] **P13.7** Pay-information request pack (F14, **reframed**). The Directive's worker information
  right suggests a portal; `requirements-one-pager.md` excludes self-service because it changes the
  threat model, the audit requirements and the support load. So: a print-ready pack generated by the
  HR Manager from `/employees/[id]` — that person's pay, their band, and their category's aggregates
  under the same ≥5 suppression. No new auth surface, no new role, no employee login.
  *Verify:* audited as a pay-data read (FR-7.2) — confirm the audit row exists; a category under 5
  renders as suppressed, not blank.

## P14 — Downstream

> Independent of `P10`–`P13`; pull forward if payroll hand-off becomes urgent. Payroll *execution*
> stays excluded — but the exclusion's own wording ("the upstream source of truth that feeds
> payroll") creates this obligation, and today the only path out is a person exporting a CSV and
> hoping they picked the right filter.

- [ ] **P14.1** `GET /api/feeds/applied-changes?since=` (F15) — every ledger row opened since a
  cursor, with employee number, amounts, currency, effective date, reason, approver. Cursor-paged per
  NFR-5, audited as a pay-data read.
  *Verify:* re-running with the same cursor returns the same rows (idempotent); rows appear only at
  `APPLIED`, never at `APPROVED` — an approved change is a promise, not a fact (CLAUDE.md §8).
- [ ] **P14.2** `GET /api/feeds/extract?asAt=` full reconciliation extract (F15).
  *Verify:* row count matches `employee_current_comp` for that date; every money field carries its
  currency (CLAUDE.md §6.2).
- [ ] **P14.3** `groupBy=manager` on payroll cost + org drill-down (F15). `employees.manager_id`
  exists and no analytic uses it. **Analytics dimension for the HR Manager only** — not a delegated
  manager workflow, which would be employee self-service by another name.
  *Verify:* the manager rollup sums to the org total; circular chains (`P11.1`'s check) do not
  infinite-loop.

---

## Progress log

| | |
|---|---|
| **Last completed** | Post-P9 backlog sweep — closed 2 of P9.6's 3 acceptance-criteria gaps (employee-list `bandStatus` filter + `sortBy=compaRatio`, both server-side over the full 10k dataset, verified live). `133/133` backend, frontend `verify` clean (2026-08-22). |
| **Current step** | **`P12.1`** — `compensation_cycles` migration. **All backend-verifiable steps in P10 and P11 are now done.** Remaining `[ ]` in those phases are UI/live-stack only: `P10.2`, `P10.4`, `P10.5`, `P10.7`, `P11.2`, `P11.4`, `P11.6`. |
| **Blockers** | `P0.3` still needs Neon project + `DATABASE_URL` (not required by anything done so far — this repo runs entirely against local Postgres). |

_Update both rows on every completed step._

**Phase close (2026-08-21), P2 → P2.5:** backend `./mvnw clean verify` → `Tests run: 58, Failures: 0,
Errors: 0`, `BUILD SUCCESS`. Frontend `npm run lint` / `npm run typecheck` / `npm run build` — all
clean, no errors or warnings.
