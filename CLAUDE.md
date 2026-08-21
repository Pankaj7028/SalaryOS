# CLAUDE.md — Salary OS

> **Root project context for Claude Code / any AI coding assistant.**
> Auto-loaded. Holds everything **shared** across the backend and the UI: what we are building, the
> architecture, the auth model, the design system, cross-cutting invariants, conventions, and
> commands.
> Layer-specific detail lives in `docs/salary-management-backend.md` and
> `docs/salary-management-ui.md`. **Read the relevant one before working inside that layer.**

---

## 0. Session start (MANDATORY — do this FIRST, every session)

1. Read this file.
2. Read `BuildPlan.md`. **The first `[ ]` (or `[~]`) step is the current step.** `git log --oneline -1`
   confirms the last completed step ID.
3. Read the layer doc for that step — `docs/salary-management-backend.md` or
   `docs/salary-management-ui.md` — plus `Technical-Requirements.md` for the FR/NFR it implements.
4. Then work. Do not start a step whose layer doc you have not read this session.

There is one developer and one build track. There is no per-developer module assignment — if you
have come from a project that had one, drop that habit here.

---

## 1. What we are building

**Salary OS** is a single-tenant, web-based **compensation management** system for ACME: 10,000
employees across multiple countries, currently managed in spreadsheets. Its user is ACME's **HR
Manager**. Its job is to hold the authoritative record of what everyone is paid, and to answer
questions about how the organisation pays people.

`requirements-one-pager.md` is the scope contract — goal, the seven questions the product must
answer, and the deliberate exclusions. **Read it before proposing any feature that is not in
`BuildPlan.md`.** If a request is on the excluded list, say so and quote the reasoning rather than
quietly building it.

Two deployables, one repository:

| Piece | Tech | Responsibility |
|---|---|---|
| `salary-service` | Spring Boot 4.0.3 (Java 17+) | Domain, persistence, auth, analytics, seeding |
| `salary-web` | Next.js 16 (App Router) + shadcn/ui | The only user interface |

This is a **modular monolith**, deliberately. One service, one schema, packages by domain module.
There is no event bus, no second service, and no inter-service contract to keep in sync — the
scale (10k employees, one HR team) does not justify any of it, and distributed state is the most
expensive thing you can add to a product whose core promise is *one authoritative number*.

---

## 2. Repository structure

```
salary-os/
├── CLAUDE.md                              ← this file (shared context)
├── BuildPlan.md                           ← resumable build tracker (checkbox steps, §2B)
├── requirements-one-pager.md              ← scope contract: goal, features, exclusions
├── Technical-Requirements.md              ← FR/NFR, data model, API contract, acceptance criteria
├── docs/
│   ├── salary-management-backend.md       ← BINDING for all salary-service work
│   ├── salary-management-ui.md            ← BINDING for all salary-web work (design system lives here)
│   └── adr/                               ← one file per reversed or contested decision
├── salary-service/                        ← Spring Boot 4.0.3
│   └── src/main/resources/db/migration/   ← Flyway, salary_schema
└── salary-web/                            ← Next.js 16 + shadcn/ui + Tailwind v4
```

---

## 2B. Build workflow & resuming (read every session)

`BuildPlan.md` is the **single source of truth for build progress**. Follow it strictly so work
survives a session ending mid-task.

- **Current step** = the first `[ ]` or `[~]` in `BuildPlan.md`.
- **Auto-continue:** after completing a step, proceed to the next `[ ]`. Loop until (a) no `[ ]`
  steps remain, (b) a *Verify* fails, or (c) the context limit is near. Never skip ahead, never
  batch several steps silently.
- **Verify before marking done.** Every step has a *Verify* clause — its compile, its targeted
  tests, its curl, its screenshot. A step is not done because the code looks right.
- **Do not run the full suite after every step.** The step's *Verify* clause is the obligation. Run
  the complete suite when a phase closes, when a change crosses a module boundary (an entity, a
  migration, a shared DTO, a token value), or when a targeted run surprises you. **Report the
  numbers you actually observed**, not "all tests pass".
- **Mark done:** `[ ]` → `[x]`, and update the *Progress log* at the foot of `BuildPlan.md`.
- **Commit:** `git commit -m "<ID> <short desc>"` (e.g. `P4.3 effective-dated comp insert`).
- **If a Verify fails or you are interrupted mid-step:** set the step to `[~]`, add a one-line
  blocker note beneath it, summarise what was completed this session, and stop.
- **If a step is too big:** split it into `<ID>.a/.b/…` in `BuildPlan.md` first, then build `.a`.

---

## 3. Tech stack (pinned)

| Layer | Tech | Version | Notes |
|---|---|---|---|
| Backend | Spring Boot | **4.0.3** | Java 17+; Spring Framework 7, Spring Security 7 |
| Persistence | Spring Data JPA + Hibernate | bundled | Flyway for migrations |
| Database | **Neon** PostgreSQL | **17** | Serverless Postgres; pooled connection string |
| Password hashing | Argon2id | Spring Security | via `DelegatingPasswordEncoder`, id `argon2` |
| Frontend | **Next.js** | **16** | App Router, Server Components by default |
| UI kit | **shadcn/ui** | latest, `new-york` | Copied into `src/components/ui/`, ours to edit |
| CSS | Tailwind | **v4** | `@theme inline`, CSS-first config, no `tailwind.config.js` |
| Data fetching | TanStack Query | v5 | Client islands only |
| Tables | TanStack Table | v8 | Headless; shadcn `Table` renders it |
| Charts | Recharts | 2.x | Themed from CSS variables, never hard-coded colours |
| Forms | React Hook Form + Zod | — | One Zod schema per form, shared with the API types |
| Fonts | IBM Plex Sans, IBM Plex Mono | `next/font/google` | See §5.2 |
| Testing | JUnit 5 + Testcontainers · Vitest + Playwright | — | Testcontainers Postgres 17, not H2 |

> **Why Testcontainers and not an embedded DB:** the schema uses a `daterange` exclusion constraint,
> `btree_gist`, and schema-qualified native SQL. H2 supports none of it, so an H2-backed test suite
> would pass while production fails. **Do not add H2.**

---

## 4. Authentication & session model

**Database-backed. There is no Firebase, no external identity provider, and no third-party token
in this system.** If a doc, comment, or habit says otherwise, it came from a different project.

### 4.1 Flow
1. `POST /api/auth/login` with email + password over HTTPS.
2. The service looks up `salary_schema.users`, verifies the password with **Argon2id**, and checks
   `status = ACTIVE` and the lockout counter.
3. On success it mints its **own signed JWT** (the *access token*), writes a row to
   `user_sessions`, and returns **two cookies**:
   - `sos_session` — the access token. `HttpOnly; Secure; SameSite=Lax; Path=/`, TTL 20 minutes.
   - `sos_csrf` — a random value, **readable by JS**, same TTL.
   - plus `sos_refresh` — `HttpOnly; Secure; SameSite=Lax; Path=/api/auth`, TTL 12 hours, one row
     in `user_sessions`, **rotated on every use with reuse detection** (a replayed refresh token
     revokes the whole session family).
4. Every later request is authenticated by validating `sos_session` **locally** — signature,
   claims, expiry, and a `jti` check against `user_sessions` for revocation.
5. Mutating requests must echo `sos_csrf` in an `X-CSRF-Token` header (double-submit).
6. `POST /api/auth/logout` clears the cookies and revokes the session row.

### 4.2 Claims
`sub` (user id), `role`, `iat`, `exp`, `jti`. Signed HS256 with `APP_JWT_SIGNING_KEY`. Nothing else
goes in the token — **never a list of employee ids, never a permission set**. The token says who you
are; the database says what you may do, freshly, on every request.

### 4.3 Roles — flat, no hierarchy
`HR_ADMIN`, `HR_MANAGER`, `COMP_ANALYST`, `AUDITOR`. One authority per user.

**`HR_ADMIN` is not implicitly allowed everything.** There is no role hierarchy and no
`RoleHierarchy` bean. A role is permitted only where it is typed out in the `@PreAuthorize`. Miss it
and that role gets a hard 403 — which is the correct failure, and the reason the RBAC table in §7 is
mirrored by a test that fails the build when the two drift.

### 4.4 Never in the browser
No token in `localStorage`, no token in a JS variable, no `Authorization` header from the client.
The cookie is the whole mechanism. A component that needs to know who the user is calls
`GET /api/auth/me`.

---

## 5. Design system (summary — `docs/salary-management-ui.md` is the contract)

> **`docs/salary-management-ui.md` is BINDING for all UI work, existing and new.** This section is
> the summary: the full token tables, per-component specs, screen layouts, empty and error states,
> and the merge checklist live there. **Read it before writing any UI code**, and run its checklist
> before marking a UI step done.

**The one rule that matters most:** never write a raw colour, size, or spacing value in a component.
Every value comes from a CSS variable defined in `src/app/theme.css`. This is what makes the two
themes work at all, and it is the single easiest rule to break.

### 5.1 Palette anchors

| Token | Light | Dark | Use |
|---|---|---|---|
| `--background` | `#FAFAFA` | `#09090B` | Page |
| `--card` | `#FFFFFF` | `#131316` | Panels, tables, dialogs |
| `--foreground` | `#18181B` | `#FAFAFA` | Body text |
| `--muted-foreground` | `#71717A` | `#A1A1AA` | Labels, secondary |
| `--border` | `#E4E4E7` | `#27272A` | Rules, inputs |
| `--primary` | `#0B6E4F` | `#34D399` | Actions, active nav, focus ring |
| `--primary-foreground` | `#FFFFFF` | `#052E22` | On primary |
| `--attention` | `#A16207` | `#FBBF24` | Below band, expiring |
| `--critical` | `#B91C1C` | `#F87171` | Destructive, above max |

Deep pine in light, **lifted to emerald-400 in dark** — on a near-black surface the primary is read
as text (links, active nav labels, delta figures) more often than as a fill, and the light value
falls under the 4.5:1 floor there. Same reason `--primary-foreground` goes near-black in dark.

### 5.2 Typography

**IBM Plex Sans** for the interface, **IBM Plex Mono** for every number. That second half is the
point: in a compensation tool the numerals *are* the display type, so money, dates, employee
numbers, and percentages are set in Plex Mono with `font-variant-numeric: tabular-nums`, and columns
of figures align on the decimal without any extra work. Base size is **14px** — this is a dense
data tool, not a marketing site.

### 5.3 Themes are `.app-light` and `.app-dark`
The class goes on `<html>`. shadcn's dark variant is rebound to our class name — `@custom-variant
dark (&:is(.app-dark *))` — so every shadcn component follows without modification. Default is
**System**; the choice persists in a cookie so the server renders the right theme and there is no
flash.

### 5.4 Shell
`.app-shell` (column) → `.app-topbar` (sticky, full width, 56px) over `.app-body` (flex) →
`.app-sidebar` (240px, collapsible to 60px, off-canvas under 768px) + `.app-content`. Full spec,
including the nav groups and the topbar's currency toggle, in `docs/salary-management-ui.md §6`.

### 5.5 Density
**Every shadcn control that takes a size renders at `sm`.** Table rows are 40px, controls 32px. One
default-size control next to small ones reads as a mistake, not a detail.

### 5.6 The signature component — `<BandBar>`
Anywhere a salary appears next to a band, it renders as a range bar: a hairline track from band
minimum to maximum, a tick at mid, and a marker at this person's position. **A salary figure shown
without its band is an incomplete answer** — that is the product's whole thesis, so it is a
component and not a one-off chart. Spec in `docs/salary-management-ui.md §7.1`.

---

## 6. Cross-cutting invariants

These hold everywhere, in both layers. Each one exists because breaking it fails **silently**.

1. **Money is `NUMERIC(15,2)` in the database and `BigDecimal` in Java. Never `double`, never
   `float`, never a JS `number` doing arithmetic.** The browser formats; it does not calculate. Any
   figure the UI displays was computed server-side.
2. **A money value never travels without its currency code.** No bare `amount` field exists in any
   DTO. The pair is `amount` + `currency`, always.
3. **Compensation is insert-only.** `compensation_records` is never `UPDATE`d. A change closes the
   open period (`effective_to`) and inserts a new row. A mistake is corrected by a new row carrying
   `change_reason = CORRECTION`, not by editing history. A `daterange` exclusion constraint stops
   overlapping periods at the database level — the service must not be the only thing enforcing it.
4. **Normalisation uses a pinned rate.** Every comp record stores `normalized_annual_base` in USD
   *and* the `fx_rate_id` used. Reports never call a live rate and never recompute historical
   figures at today's rate — a number that changes between two runs of the same report destroys
   trust in every other number on the page.
5. **Every native query names its schema.** `salary_schema.employees`, never `employees`.
   `hibernate.default_schema` rewrites entity-mapped SQL only; an unqualified native query resolves
   against the connection's `search_path`, which on Neon is `public`, and fails only in production.
   `NativeQuerySchemaQualificationTest` fails the build on an unqualified name.
6. **Demographic attributes are isolated.** `employee_demographics` is a separate table, has no
   entity relationship that lets a JPA fetch drag it into an employee response, and is **never
   rendered per person, anywhere, at any role**. It reaches the UI only as an aggregate over a
   cohort of **five or more** people. `DemographicsIsolationTest` fails the build if a demographic
   field name appears in any DTO outside the analytics package.
7. **Every read of individual pay data is audited**, not only writes. "Who looked at what salaries"
   is a question ACME's auditors will ask, and it cannot be answered retroactively.
8. **Dates are `LocalDate` for effective dating and UTC `Instant` for events.** An effective date is
   a calendar fact in the employee's country, not a moment in time; storing it as a timestamp puts
   raises a day early for half the org.

---

## 7. RBAC (overview — mirrored by `RolePermissionMatrixTest`)

| Capability | HR Admin | HR Manager | Comp Analyst | Auditor |
|---|:-:|:-:|:-:|:-:|
| Manage users & roles | ✅ | ❌ | ❌ | ❌ |
| View employees & their pay | ✅ | ✅ | ✅ | ✅ |
| Create / edit employee record | ✅ | ✅ | ❌ | ❌ |
| Propose a compensation change | ✅ | ✅ | ✅ | ❌ |
| Approve / reject a change | ✅ | ✅ | ❌ | ❌ |
| Manage salary bands & levels | ✅ | ✅ | ❌ | ❌ |
| Run insights (aggregate) | ✅ | ✅ | ✅ | ❌ |
| Import / bulk upload | ✅ | ❌ | ❌ | ❌ |
| Read the audit log | ✅ | ❌ | ❌ | ✅ |

**Nobody, at any role, sees a demographic attribute attached to a named person.** That is not a
permission — it is an absence. There is no endpoint that returns one.

**Hiding a nav item is not access control.** The sidebar is filtered by
`NAV_VISIBILITY` in `salary-web/src/lib/auth/roles.ts`; the boundary is the `@PreAuthorize` on the
controller. Visibility may be narrower than access, never wider — `roles.test.ts` asserts it.

---

## 8. Compensation change lifecycle

```
DRAFT ──submit──▶ PENDING ──approve──▶ APPROVED ──(effective date reached)──▶ APPLIED
  │                  │
  └──discard──▶ ✕    └──reject──▶ REJECTED
```

- Only `APPLIED` writes a `compensation_records` row. `APPROVED` is a promise, not a fact — a change
  approved in March with an effective date in July must not appear in June's payroll cost.
- The proposer cannot approve their own change (`ProposerIsNotApproverTest`).
- An employee may have at most one non-terminal change at a time; a second proposal is a 409.
- Applying is a scheduled job (daily, 02:00 UTC) **plus** an idempotent manual trigger, because a
  scheduled job that silently misses a day is how people get paid the wrong amount.

---

## 9. Coding conventions

**Java (`salary-service`):** base package `com.acme.salaryos`; packages by module
(`employee`, `compensation`, `band`, `change`, `analytics`, `auth`, `audit`, `seed`), each with
`web` / `service` / `domain` / `repository` / `dto` inside; layered `controller → service →
repository`. **Lombok is mandatory** — `@Getter`/`@Builder`/`@RequiredArgsConstructor` on entities
and DTOs, `@Slf4j` on services and controllers; no hand-written getters, no
`LoggerFactory.getLogger`. Constructor injection only. DTOs are Java `record`s. Prefer derived query
methods; when native SQL is unavoidable, qualify the schema (§6.5). One Flyway migration per change,
never edited after commit.

**TypeScript (`salary-web`):** App Router, **Server Components by default, a client island only
where something must be interactive**. Data fetchers live in `src/lib/api/<domain>.ts` and know
nothing about React; TanStack Query hooks sit in a sibling `<domain>-queries.ts`; every cache key
comes from `src/lib/api/keys.ts`. **List, filter, sort, and tab state lives in the URL**
(`searchParams`), never in component state — an HR Manager sends a filtered view to a colleague by
sending the link. Toasts go through `src/lib/notify.ts` only (one `<Toaster>`, at the root).
**Never sync props into state in a `useEffect`** — it paints a wrong frame first and
`react-hooks/set-state-in-effect` fails the build. Verify a framework API against
`node_modules/next/dist/` rather than from memory; Next 16 renamed `middleware.ts` to **`proxy.ts`**.

---

## 10. Environment & config

| Key | Where | Notes |
|---|---|---|
| `DATABASE_URL` | service | Neon pooled connection string, `?sslmode=require` |
| `DATABASE_SCHEMA` | service | `salary_schema` |
| `APP_JWT_SIGNING_KEY` | service | HS256, ≥ 32 bytes, rotated by config |
| `APP_SESSION_TTL` / `APP_REFRESH_TTL` | service | Default 20m / 12h |
| `APP_COOKIE_DOMAIN` / `APP_COOKIE_SAMESITE` | service | Cookie attributes per environment |
| `APP_CORS_ORIGINS` | service | Explicit allow-list; `allowCredentials=true` |
| `APP_SEED_RANDOM_SEED` | service | Default `20260820` — reproducibility depends on it |
| `APP_BASE_CURRENCY` | service | `USD`; the normalisation target |
| `NEXT_PUBLIC_API_BASE_URL` | web | Service origin |

---

## 11. Common commands

```bash
# Backend (in salary-service/)
./mvnw spring-boot:run
./mvnw clean verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed      # seed 10,000 employees
./mvnw flyway:info

# Frontend (in salary-web/)
npm install && npm run dev
npm run build && npm run lint && npm run typecheck
npx shadcn@latest add <component>
```

---

## 12. Rules for the AI agent

1. **Read `BuildPlan.md` and the layer doc before working (§0).** Work the current step, not the
   step you find interesting.
2. **`requirements-one-pager.md` is the scope contract.** If a request is on the exclusion list,
   name the exclusion and its reasoning before building anything.
3. **Never overwrite a compensation record (§6.3).** Every pay change is an insert. If you find
   yourself writing `UPDATE compensation_records SET base_amount`, stop — you are about to delete
   history that the audit log cannot reconstruct.
4. **Never let a money value lose its currency (§6.2)**, and never do money arithmetic in
   TypeScript. If a figure is needed, add it to the API response.
5. **Schema-qualify every native query (§6.5).** This is the failure mode that reaches production
   intact.
6. **Demographics never reach an individual response (§6.6).** Aggregate, cohort ≥ 5, or not at all.
   If a screen design seems to need a person's demographic attribute, the design is wrong.
7. **`docs/salary-management-ui.md` is binding for every screen.** No raw hex, no off-scale font
   size, no off-grid spacing. Run its §12 checklist before marking UI work done.
8. **Every salary shown gets its band shown (§5.6).** A bare number is not an answer.
9. **Toasts through `notify.ts`, one `<Toaster>` at the root.** Never a second one, never a
   component-scoped provider — it silently shadows the root and nothing appears and nothing errors.
10. **Every role decision reads `src/lib/auth/roles.ts`** — never a role literal in a route, a nav
    item, or a component.
11. **A migration is immutable once committed.** Fix forward with a new one.
12. **Do not add H2, and do not weaken a test to make it pass.** A failing Testcontainers test about
    an overlapping date range is the constraint doing its job.
13. **Say what you actually ran.** Report observed test counts and timings, not "everything passes".

---

## 13. Pointers

- **Scope contract:** `requirements-one-pager.md`
- **Requirements, data model, API, acceptance criteria:** `Technical-Requirements.md`
- **Build tracker:** `BuildPlan.md`
- **Backend (BINDING):** `docs/salary-management-backend.md`
- **UI + design system (BINDING):** `docs/salary-management-ui.md`
