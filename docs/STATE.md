# STATE.md — carry-over between sessions

**Purpose:** everything a fresh session needs that is *not* derivable from the code, the docs, or
`git log`. This file lives in the repo on purpose — it survives a new Claude account, a new machine,
a different model, and a cleared context window. Assistant memory directories do not.

**Contract:** keep it under ~120 lines. It is loaded every session, so every line costs tokens
forever. When a fact becomes true in the code, delete it from here — the code is then the record.

---

## Where we are

| | |
|---|---|
| **Phase** | P6 (Changes & approval) in progress |
| **Last completed** | `P6.1` Change lifecycle endpoints — `[x]`, 102/102 backend tests. |
| **Next step** | `P6.2` — `ApplyDueChangesJob` (daily 02:00 UTC) + idempotent `POST /changes/apply-due`. |
| **Blockers** | No Neon project yet (`P0.3`, not required by anything built so far). |

`BuildPlan.md` is the authority on step status; this row is the fast path. If they disagree,
`BuildPlan.md` wins.

Done: `P0.1` `P0.2` `P0.4` · `P1` · `P2` (all) · `P3.1`–`P3.7` · `P4` (all) · `P5` (all) · `P6.1`.
Blocked: `P0.3`. Untouched: `P6.2`–`P9`.

---

## Docker is unblocked — colima, not Docker Desktop

`DOCKER_HOST`/`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` are in `~/.zshrc`, but **the Bash tool's shell
doesn't source it per call** — export both explicitly alongside any Maven command that needs Docker:
`export DOCKER_HOST=unix:///Users/pankajmandal/.colima/default/docker.sock` and
`export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`. `colima status`/`colima start`
as needed.

---

## No Neon yet — manual verification recipe (throwaway local Postgres)

`P0.3` (a real Neon `DATABASE_URL`) and `P9` (`SeedRunner`) don't exist yet. For a UI step that
needs real data: `docker run -d --name salaryos-devdb -e POSTGRES_PASSWORD=devpass -e
POSTGRES_DB=salaryos -p 5433:5432 postgres:17`. Create `salary-service/src/main/resources/
application-local.yml` (git-ignored; `application-local.yml.example` is the template) pointing at
`localhost:5433`, with `spring.autoconfigure.exclude: []` (overriding the P0.2 stub in
`application.yml`) and `app.cors.allowed-origins` matching the frontend's port. Run `./mvnw
spring-boot:run -Dspring-boot.run.profiles=local` — Flyway migrates on startup. Seed by hand in FK
order (departments → locations → job families/levels → salary bands → fx_rates → employees →
compensation_records → employee_current_comp); a worked example covering every `BandBar` state plus
a 5-person peer cohort is in the P4.3/P4.4 done-notes in `BuildPlan.md`. Login user password hash
needs the `{argon2}` prefix (`DelegatingPasswordEncoder` keyed `"argon2"`) — generate via `jshell`:
`./mvnw -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt` then `echo 'import
org.springframework.security.crypto.argon2.Argon2PasswordEncoder; var enc =
Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(); System.out.println("{argon2}" +
enc.encode("Password123!")); /exit' | jshell --class-path "target/classes:$(cat /tmp/cp.txt)" -q`.
Frontend: `npx next start -p 3100` (after `npm run build` — see "verify against `next start`" gotcha
below), `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` in `.env.local`.

---

## Environment facts (verified 2026-08-21)

- **Java 25**; toolchain targets **17**. Do not raise `maven.compiler.release` without an ADR.
- **No system Maven** — use `./mvnw`. **Node v26.3.0 / npm 11.16.0.** `~/.m2` is populated for Boot
  4.0.8, so the backend builds offline.
- **`npm install @tanstack/react-table` installs v9 by default** (breaking rewrite — no
  `useReactTable`/`getCoreRowModel`). CLAUDE.md pins v8: `npm install @tanstack/react-table@^8`.

---

## Decisions taken outside the docs

- **Layer docs moved to `docs/`** at P0.1; the assessment PDF stays at the repo root as an input.
- **`.claude/settings.json` is committed; `.claude/settings.local.json` is git-ignored.**
- **Spring Boot pinned to 4.0.8**, not the docs' original 4.0.3 — same minor line, five patch
  releases of fixes. Every doc and the pom were updated together.
- **shadcn is 4.x `radix-nova`, not `new-york`** (v4 removed new-york) — Radix chosen over v4's
  `base` default because the UI doc's screens assume Radix primitives.
- **One `.gitignore`, at the root.** Add new ignores there, not in a package subdirectory.
- **`getCurrentUser()` in `src/lib/auth/current-user.ts` is a placeholder**, the seam where
  `GET /api/auth/me` lands at P2.5. Change `role` there to view the app as another role.
- **P4.3's Employees list omits column sort, a "band status" filter, "page N" jump, saved views, and
  bulk-select; `GET /employees/{id}/peers` (FR-6.6) was built at P4.4, not P7.5 (P7.5's real
  remaining scope is just `increase-cycle`, FR-6.5); `TooltipProvider` lives once in
  `query-provider.tsx`** — full reasoning for each in the P4.3/P4.4 done-notes in `BuildPlan.md`.
- **FTE annualisation (P5.1, user-confirmed):** grossed to FTE = 1.0 (÷ FTE) for `ANNUAL`/`MONTHLY`;
  `HOURLY` is the exception — `amount × 2080` (documented nowhere else) already IS that figure, so
  no further ÷ FTE. `app.base-currency` (`APP_BASE_CURRENCY`, default `USD`) added the same step.
- **`employee_current_comp` is refreshed by `EmployeeCurrentCompProjector`** (P5.2) from
  `EffectiveDating.apply()`/`.correct()` and `EmployeeService.terminate()` — any future write that
  opens/closes/supersedes a `compensation_records` row must call `projector.refresh(employeeId)`
  too, or the projection silently goes stale for that employee.
- **`@AuthenticationPrincipal UUID currentUserId`** is the "who is calling this" pattern (P5.3,
  first use) — `SessionCookieAuthFilter` sets the JWT `sub` claim as the principal directly. Use
  this, not `SecurityContextHolder` by hand, for any future write needing the acting user (P6).
- **CSV import format (P5.3, undocumented elsewhere):** header row + domain-field columns (bands:
  `jobLevelId,countryCode,currency,minAmount,midAmount,maxAmount,effectiveFrom,note`), manual
  line-split (no comma can appear in any of these fields, so no CSV library). One bad row → `ERROR`,
  never blocks the rest. `P8.4`'s employee import should probably match this shape.
- **`salary_bands` has no exclusion constraint** (unlike `compensation_records`) — `BandService`
  alone prevents overlapping versions. Be careful with any direct-repository write to this table.
- **Termination pay runs through and includes `terminationDate`** (user-confirmed, P5.4) —
  `EmployeeService.terminate()` closes at `terminationDate.plusDays(1)`, not `terminationDate`
  itself (same `[)`-range reasoning as the closing-formula gotcha above).
- **React Hook Form + Zod installed and used for real at P5.5** (CLAUDE.md's pinned forms stack;
  sign-in's plain `useState` predates this and wasn't redone). `bandFormSchema` is the pattern to
  follow for P6's propose-change dialog: one shared Zod schema per form, `.refine()` for
  cross-field checks mirroring (never replacing) the backend's own validation.
- **`compensation_changes.newBaseAmount`/`currentBaseAmount` are annual figures** (P6.1) — the
  table has no `pay_frequency` column at all, unlike the ledger, so a proposal is always "their new
  annual salary." One shared `currency` column means a proposal must match the employee's current
  pay currency (`ChangeCurrencyMismatchException` otherwise). `NO_BAND` does **not** trigger
  FR-5.4's mandatory-note rule — only an existing band that the new amount falls outside of does.

---

## Gotchas found the hard way

- **Boot 4 moved every autoconfiguration class into a per-module package** and **renamed the
  starters** (`spring-boot-starter-webmvc`, not `-web`; test support per module). A wrong class name
  in `spring.autoconfigure.exclude` is **ignored silently** — confirm against `AutoConfiguration.
  imports` inside the jar.
- **Jackson 3 lives under `tools.jackson.*`, not `com.fasterxml.jackson.*`** (the latter is only
  transitive via JJWT/Jackson 2, not a Spring bean). No Jackson-3 `jackson-annotations` artifact is
  on the classpath, so `@JsonFormat` etc. isn't available without a new dependency — not needed yet
  (money serializes as a JSON number; every consumer already copes).
- **If a connected Chrome tab can't reach this machine's `localhost`, check for a second paired
  browser on a different physical machine before giving up.** Burned most of a session (P4.3–P5.4)
  believing "Chrome can't reach localhost" was a hard limitation — `tabs_context_mcp` was silently
  defaulting to a Linux-paired browser alongside the correct macOS one. Fix:
  `list_connected_browsers` to see all paired devices, `select_browser(deviceId)` to pick the right
  one. Only conclude "genuinely can't reach it" after confirming you're on the right browser.
- **Hibernate flushes every pending INSERT before any UPDATE, regardless of the order your Java
  code called `save()` in.** Bit `EffectiveDating.apply()`/`.correct()` at P5.1: closing an old
  ledger row and inserting its replacement, called in the "right" order, still hit the DB
  new-row-first and tripped `comp_no_overlap` (both rows briefly open-ended). Fix: `saveAndFlush()`
  the closed/updated row before building the new entity to insert. Watch for this anywhere a write
  path closes one row and opens another in the same transaction (P6's change-apply job will too).
- **Closing an effective-dated row = `close(newFrom)`, never `newFrom.minusDays(1)`** — found at
  P5.4, was wrong in both `EffectiveDating` and `BandService` since P5.1/P5.3 (fixed there and in
  `docs/salary-management-backend.md` §3 rule 1, whose stated formula was itself the bug). Every
  `validity`/effective-dated range in this schema is `[)` — inclusive start, **exclusive** end — so
  subtracting a day leaves the day before a new period/version covered by *neither* row. Verified
  against real Postgres: `daterange('a','2024-07-01','[)') @> '2024-06-30'` is `false`. If you ever
  write a new "close this row, open a successor" path, `close(successor.effectiveFrom)`, full stop.
- **Shared cached Testcontainers context (same `@SpringBootTest` properties block ⇒ same container)
  keeps burning tests that assume they own a table or an `fx_rates` month exclusively** — three
  separate instances fixed now (`EmployeeListPaginationTest` at P4.1, `V4V5BandsAndFxMigrationTest`
  and an `fx_rates` date collision at P5.1). Any new test doing an unscoped `count(*)` or reusing a
  "round" date/month is a ticking time bomb — scope counts to your own seeded key, and pick
  boring-but-unique dates (`EffectiveDatingTest` uses 2031 for exactly this reason).
- **Verify UI steps against `next start`, not `next dev`** — the dev overlay swallows clicks (ate
  the sidebar Collapse button). Use `BASE_URL=…:3100`. Playwright: `waitUntil: "load"`, not
  `"networkidle"` (never fires against `next start`).
- **`command.tsx`/`sonner.tsx` shadcn components were repaired in place** (ours to edit) — don't
  re-pull them from upstream. **`salary-web/AGENTS.md`/`CLAUDE.md` are `next dev`-generated and
  committed deliberately** — deleting them only produces churn.
- **`npm install` from the wrong cwd fails silently.** Running it from the repo root instead of
  `salary-web/` (persisted-shell cwd drift after backend work) created a stray, untracked root
  `package.json`/`node_modules` — typecheck/lint/build all still passed, because Node's module
  resolution walks up the directory tree and found the packages there anyway. Always confirm `pwd`
  (or use an absolute `cd` in the same command) before any `npm install` in this repo.
- **Neither the Employees table nor the Bands grid degrades to cards at 375px** (CLAUDE.md/ui doc
  §12.10) — a known, unfixed gap. shadcn's `Table` wrapper has its own `overflow-x-auto`, so a
  narrow viewport scrolls the table internally rather than the page, but that's not the same thing.

---

## Verification entry points

`npm run verify` (tokens + contrast + lint + typecheck + tests + build) closes a UI step. Browser-
driven checks need `next start` on :3100 — `verify:visual` `verify:shell` `verify:topbar`
`verify:components`. Backend: `./mvnw clean verify` (Testcontainers, needs the Docker env vars above).
