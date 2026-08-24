**Subject:** Salary OS — assessment submission

Hi <Name>,

I've completed the salary management assessment. It's deployed and running, and the repository
carries the artifacts alongside the code.

**Live app:** https://salary-os.vercel.app/
**Repository:** https://github.com/Pankaj7028/SalaryOS
**Product overview:** attached — a 16-page walkthrough built from screenshots of the deployed app.

Sign in with `admin@acme.test` / `uF6kfSsZwPYLuKn4fIa5` (HR Admin, so it sees every screen).

Salary OS is a compensation system for ACME's HR Manager: 10,000 seeded employees across seven
countries, ~49,700 effective-dated pay periods, 470 salary bands. The thesis is one sentence — *a
salary shown without its band is an incomplete answer* — and it drives the design throughout.

I wrote `requirements-one-pager.md` first. It names the seven questions the product answers and an
explicit exclusion list with reasoning: no payroll execution, no employee self-service, no benefits
or equity modelling, no configurable approval chains, no live FX rates.

**Decisions worth calling out**

- **Compensation is insert-only.** A change closes the current period and inserts a new row;
  mistakes are corrected by a further row, never an UPDATE. A Postgres `daterange` exclusion
  constraint enforces non-overlapping periods at the database level.
- **Money never loses its currency, and never gets computed in the browser.** `NUMERIC(15,2)` /
  `BigDecimal`, every DTO carrying `amount` + `currency` as a pair. The UI formats; it doesn't calculate.
- **FX normalisation uses a month-pinned rate, stored per record.** A report whose numbers change
  between two runs is not a report.
- **Demographics are an absence, not a permission.** Isolated table, aggregate-only, cohort ≥ 5. No
  endpoint at any role returns a demographic attribute attached to a named person.
- **Approved is a promise; applied is a fact.** Only `APPLIED` writes to the ledger, so a raise
  approved in March with a July effective date stays out of June's payroll cost.
- **Roles are flat.** HR Admin isn't implicitly allowed everything — a role is permitted only where
  it's typed into the `@PreAuthorize`, and a test fails the build when that drifts from the docs.

**Tests** — 251 passing: 206 backend (JUnit 5 + Testcontainers against real PostgreSQL) and 45
frontend (Vitest). No embedded database on purpose: the schema needs `daterange`, `btree_gist`, and
schema-qualified native SQL, so an H2 suite would pass while production failed. Several tests exist
to catch otherwise-silent failures — unqualified native queries, demographic fields leaking into
non-analytics DTOs, a proposer approving their own change.

**Stack** — Spring Boot 4.0.8 / Java 17 · PostgreSQL on Neon · Flyway · Argon2id · Next.js 16 ·
shadcn/ui · Tailwind v4 · TanStack Query + Table · Recharts. A modular monolith on purpose: at this
scale, distributed state is the most expensive thing you can add to a product whose promise is one
authoritative number. Deployed on Vercel, Render, and Neon.

**On AI use** — the repository is the artifact. `CLAUDE.md` is the standing context I gave the agent;
`BuildPlan.md` is the resumable tracker it worked against, one verified step per commit, across 96
incremental commits.

One thing worth knowing: the seeded bands sit low relative to seeded salaries, so the out-of-band
screen reports 6,911 of 9,580 people outside their band. The figures reconcile exactly — the number
is a property of the fixture, not a plausible org. I left it visible rather than tuning the seed to
flatter the screenshot.

Happy to walk through any of it.

Best regards,
Pankaj Mandal
