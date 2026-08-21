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
- [ ] **P1.3** `V3` — `employees`, `employee_demographics` (no FK from employee to demographics
  in JPA — see `CLAUDE.md §6.6`). *Verify:* migrate clean.
- [ ] **P1.4** `V4` + `V5` — `salary_bands` (with the ordering check), `fx_rates` (unique on month +
  pair). *Verify:* the band check constraint rejects `min > mid`.
- [ ] **P1.5** `V6` — `compensation_records` with the generated `validity` column and the
  `EXCLUDE USING gist` constraint, plus `compensation_components`.
  *Verify:* an integration test inserting two overlapping periods for one employee **fails at the
  database**, and the error surfaces as the constraint name.
- [ ] **P1.6** `V7` — `compensation_changes` + the partial unique index for one open change.
  *Verify:* a second `PENDING` change for the same employee is rejected by the index.
- [ ] **P1.7** `V8` + `V9` — `audit_events` (append-only grants), `employee_current_comp`.
  *Verify:* an `UPDATE` on `audit_events` as the app role is denied.
- [ ] **P1.8** `V10` + `V11` — indexes and static reference rows.
  *Verify:* `\di salary_schema.*` matches `Technical-Requirements.md §4.3`.
- [ ] **P1.9** JPA entities + repositories for everything above; `Money` value type and converter.
  *Verify:* context loads; a round-trip test per entity; **no `double` anywhere** (grep).
- [ ] **P1.10** `NativeQuerySchemaQualificationTest`. *Verify:* it fails when you temporarily
  unqualify a table name, and passes when you restore it. Prove both directions.

## P2 — Auth end to end

- [ ] **P2.1** `SecurityConfig`, `SessionCookieAuthFilter`, JWT mint/validate, Argon2id encoder.
  *Verify:* unit tests for token validity, expiry, tampering, and revoked `jti`.
- [ ] **P2.2** `POST /auth/login`, `/logout`, `/refresh` with rotation and family revocation on
  reuse; `GET /auth/me`. *Verify:* integration test covering the full cycle **plus** the reuse case
  revoking the family.
- [ ] **P2.3** Lockout after 5 failures, uniform response and timing for wrong password / unknown
  email / locked. *Verify:* a test asserting identical status, body, and comparable timing.
- [ ] **P2.4** `@PreAuthorize` on every endpoint stub + `RolePermissionMatrixTest` against
  `CLAUDE.md §7`. *Verify:* it fails when you delete one annotation.
- [ ] **P2.5** Frontend: `proxy.ts` (Next 16's `middleware.ts`) redirecting unauthenticated routes,
  sign-in page, `useSession`, fetch wrapper with CSRF header, central 401/403/network handling.
  *Verify:* sign in as each seeded role, land on `/`, sign out, protected route bounces.

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

- [ ] **P4.1** `GET /employees` with search, filters, sort, keyset pagination; `GET /employees/{id}`.
  *Verify:* integration test pages to the end of 10k rows with no duplicate or skipped id.
- [ ] **P4.2** Create, edit, terminate; the band-mismatch flag on level/location change.
  *Verify:* terminating closes the open comp period on the termination date.
- [ ] **P4.3** Employees list screen: filter row, table, band bar column, URL state, CSV export.
  *Verify:* every filter and sort survives a reload; export matches the on-screen filter.
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
| **Last completed** | `P1.2` `V2` reference tables migration (2026-08-21) |
| **Current step** | `P1.3` — `V3` employees, employee_demographics |
| **Blockers** | `P0.3` still needs Neon project + `DATABASE_URL` (not required by P1) |

_Update both rows on every completed step._
