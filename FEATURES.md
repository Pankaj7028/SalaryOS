# Salary OS — what it does

A compensation management system for ACME: **10,000 employees, multiple countries, one
authoritative record of what everyone is paid.** It replaces the spreadsheets, and it answers
questions about how the organisation pays people without exporting anything.

Its user is ACME's HR Manager. Its thesis is one sentence: **a salary figure shown without its band
is an incomplete answer** — so the product is built to always show both, and to be able to prove
where every number came from.

Every figure quoted below is a real reading from the running system against the seeded 10,000-person
organisation, taken on 2026-08-24 — not an illustration. Some of them move: the scheduled job that
applies approved changes on their effective date ran at 02:04 UTC that morning and moved payroll cost
by $151,326.58, which is the lifecycle doing exactly what it is supposed to do.

---

## 1. The seven questions

The product exists to answer these. Each is on screen, filterable, and backed by a single query — no
export step.

| # | Question | Where | What it says today |
|---|---|---|---|
| 1 | What do we spend on base pay — total, and by country, department, level? | `/insights/pay` · `/` | **$1,140,963,472.34** across 9,580 people. Switch the basis to total target cash and it becomes **$1,202,064,009.60** |
| 2 | Who is paid **outside their band**, and what would it cost to fix? | `/insights/pay` | 6,911 people — 215 below minimum, 6,696 above maximum — with the cost to bring everyone to minimum |
| 3 | What does the **compa-ratio distribution** look like by department and level? | `/insights/pay` · `/` | Bucketed distribution, filterable by department and level |
| 4 | For the same level in the same location, **do groups get paid differently**? | `/insights/equity` | Unadjusted **and** level-adjusted, reported separately, aggregate only, minimum cohort of five |
| 5 | What did the last increase cycle cost? | `/insights/pay` | Total increase, by reason code, over any date range |
| 6 | Before I approve this raise, **how does it compare to peers**? | `/employees/{id}` | p25 / median / p75 for the same job level and country, and this person's percentile in it |
| 7 | What is one person's **full pay history** — every change, when, why, approved by whom? | `/employees/{id}` | The complete effective-dated ledger, newest period first |

Two more the build added because the migration needed them:

| Question | Where | Today |
|---|---|---|
| **Can I trust this data at all?** | `/admin/data-health` | Nine named checks. Currently 2 failing: 33 employees with no matching band, 357 reporting to a terminated manager |
| **Is the band structure itself any good?** | `/bands` | 470 in-force bands judged: 1 promotion cliff, 15 with no incumbents, 0 stale |

---

## 2. The employee register

**10,000 employees**, searchable and filterable by department, location, country, job level,
employment status, and band status, sorted by name or by compa-ratio.

- **Real keyset pagination over the full 10k** — not a client-side filter of one page. Sorting by
  compa-ratio walks a server-side keyset too, so page 40 of "worst-paid relative to band" is one
  query.
- **A result count and a page jump.** "1–50 of 9,580" rather than 50 rows and a Next button — the
  same screen whether the filter matched 51 people or 5,100 is not an answer you can size.
- **Bulk select → propose a change for everyone selected**, expressed as a percentage. Each new
  salary is computed server-side from what that person is actually paid, in their own currency.
- **Every filter lives in the URL.** An HR Manager sends a filtered view to a colleague by sending
  the link, and it opens identically.
- **Saved views.** Name a question, save it, share it. The view stores the *question*, never the
  answer — reopening it re-runs the query against today's data with the opener's own permissions.
- **CSV export** that always matches the on-screen filter.

Each person's page carries their identity, manager, current pay, the band that frames it, their
full history, and their peer comparison.

---

## 3. The compensation ledger

**Pay is insert-only.** A change never overwrites anything: it closes the current period and opens a
new one. A mistake is corrected by a new row carrying `CORRECTION`, not by editing history.

This is enforced in the database, not just in code — a Postgres `daterange` exclusion constraint
makes overlapping pay periods impossible to insert. The service is not the only thing standing
between the ledger and a corrupted history.

- Effective-dated base pay per employee: **49,692 pay periods spanning December 2017 to today**,
  about five per person.
- Recurring components — bonus target, allowances — alongside base.
- "What was this person paid on 3 March 2024?" is a supported query, answered from the ledger.
- Every row records the reason, the proposer, the approver, and the FX rate it was normalised at.

---

## 4. Salary bands and `<BandBar>`

Min / mid / max per job level per country, effective-dated and versioned. **470 bands in force.**

`<BandBar>` is the signature component: wherever a salary appears, its band appears with it as a
range bar — a track from minimum to maximum, a tick at mid, a marker at this person's position.

Two details in it are load-bearing:

- **An out-of-band salary sits *outside* the track**, overshooting the capped end. Clamping the
  marker to the end would draw someone paid below minimum as sitting healthily *at* the minimum —
  the exact error the product exists to surface.
- **"No band" is its own state** — a dashed outline naming what is missing, linking to band setup.
  It is never a full track with a centred marker, which would invent a band that does not exist.

**Market benchmarks** (where a survey has been imported) render as a separate tick above the track,
in the band's own currency. Salary OS deliberately ships the *seam* for market data rather than a
dataset — benchmarking is a data business, and a single-tenant tool has no contributor network — so
most bands carry no benchmark, which is the normal case rather than a degraded one. A median that falls outside the band draws no tick at all and says so in
words — pinning it to the track end would claim "the market is exactly at your maximum", which is
wrong precisely when it matters most.

**Band health** judges the structure rather than just storing it: range spread, midpoint progression
between adjacent levels, promotion cliffs, bands with no incumbents, and staleness. A *gap* between
adjacent bands is flagged critical — someone promoted out of the top of one band can land at the
bottom of the next and go backwards. An *overlap* is not flagged, because overlapping bands are
normal and healthy.

---

## 5. Changes and approval

```
DRAFT ──submit──▶ PENDING ──approve──▶ APPROVED ──(effective date)──▶ APPLIED
  │                  │
  └──discard──▶ ✕    └──reject──▶ REJECTED
```

- **Only `APPLIED` writes to the ledger.** `APPROVED` is a promise, not a fact — a raise approved in
  March effective in July must not appear in June's payroll cost, and it doesn't.
- **A live impact panel** before you propose: the delta, the resulting compa-ratio, where it lands in
  the band, and what it does to the peer distribution.
- **The proposer cannot approve their own change.** Enforced server-side and tested.
- **One non-terminal change per employee** — a second proposal is a 409, not a silent duplicate.
- **Bulk merit upload** by CSV, and **bulk propose** from a selection on the employee list. Partial
  success is the contract: one person who already has an open change never costs the other 39 their
  proposals.
- Applying is a **daily scheduled job plus an idempotent manual trigger** — a scheduled job that
  silently misses a day is how people get paid the wrong amount.

---

## 6. Multi-currency that doesn't drift

Pay is stored in the currency it is paid in. Every figure is *also* normalised to USD — but at a
**month-pinned FX rate stored on the row**, never a live one.

That single decision is why **running the same report twice gives the same number.** Reports never
recompute historical figures at today's rate; a number that changes between two runs destroys trust
in every other number on the page.

- The topbar toggles every money figure on screen between "as paid" and normalised.
- Responses report the **FX span** they were normalised over — how many distinct rate months, which
  currencies — because a population of 9,580 people has no single governing rate month.
- An **FX coverage matrix** on `/admin/fx-rates` shows currency × month, so you can see which months
  payroll cannot yet be written for *before* a report comes back short.
- A money value never travels without its currency code. There is no bare `amount` field in any
  response, anywhere.

---

## 7. Access control and audit

Database-backed accounts. **Four flat roles, no hierarchy:** HR Admin, HR Manager, Comp Analyst,
Auditor.

`HR_ADMIN` is deliberately *not* implicitly allowed everything. A role is permitted only where it is
typed out on the endpoint — and a test mirrors the permission table and fails the build if the two
drift.

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

Note the two deliberate absences: **HR Manager cannot read the audit log**, and **Auditor cannot run
insights**. Both are the point of having no role hierarchy.

**Sessions:** the service mints its own signed JWT, delivered as an `HttpOnly` cookie with a
20-minute life, a 12-hour refresh token rotated on every use with reuse detection, and double-submit
CSRF. No token ever reaches JavaScript, `localStorage`, or an `Authorization` header. There is no
third-party identity provider anywhere in the system.

**Every *read* of individual pay data is audited, not only writes** — "who looked at what salaries"
is a question auditors ask, and it cannot be answered retroactively. The audit log is filterable,
exportable, and readable by the Auditor role without granting them anything else.

---

## 8. Demographics: an absence, not a permission

`employee_demographics` is a separate table with no entity relationship that could let an ORM fetch
drag it into an employee response. **No endpoint returns a demographic attribute attached to a named
person, at any role.** There is no permission that unlocks it, because there is nothing to unlock.

Demographic data reaches the interface only as an aggregate over a cohort of **five or more people**,
and the equity screen states the suppression count at the top unconditionally — even at zero — so
"nothing was suppressed" is itself a visible, checked fact rather than an absence you have to infer.

A build-time test fails if a demographic field name appears in any response object outside the
analytics package.

---

## 9. Data health — the screen for day one

Salary OS exists to replace spreadsheets, and the first job of that migration is finding what the
spreadsheets got wrong. Every other screen assumes the data is right and answers a question with it;
`/admin/data-health` asks whether the data can carry the question at all.

Nine named checks, each with a severity, a count, an explanation, and **a drill-through to the actual
people**:

- Active employees with no pay record
- Terminated employees still being paid
- Pay starting before the hire date
- Circular management chains
- Employees with no matching salary band
- Level or location changed since pay last did
- Reporting to a terminated manager
- Full-time employees below 1.0 FTE
- Paid in a non-default currency for their country

Passing checks stay on screen, dimmed — a console that hides them is indistinguishable from one that
never ran them, and "we checked and it's clean" is most of the value of a health check.

The drill-through reuses the ordinary employee list, so it inherits that endpoint's permissions and
its audit trail rather than needing its own.

---

## 10. Screens

| Route | What it is |
|---|---|
| `/` | Overview — six headline figures, pay by country, compa-ratio distribution, the approval queue |
| `/employees` | The register: search, filter, sort, page, select, save the view, export |
| `/employees/{id}` | One person: current pay in band, full history, peer comparison, propose a change |
| `/changes` | The approval queue by status, with inline approve/reject |
| `/bands` | Job level × country grid with version history, and band health over it |
| `/levels` · `/locations` | Reference data browsers |
| `/insights/pay` | The saved-question library: cost, out-of-band, distribution, increase cycle |
| `/insights/equity` | Pay gap — unadjusted and level-adjusted, reported separately |
| `/insights/reports` | Headcount breakdown and the CSV exports |
| `/admin/users` | Accounts and roles |
| `/admin/import` | CSV import hub — employees, bands, merit cycles. Every file is dry-run first: the diff renders, nothing is written, and only then does "Apply import" send the identical file for real |
| `/admin/fx-rates` | Rates by month, plus the coverage matrix |
| `/admin/audit` | The audit log, filterable and exportable |
| `/admin/data-health` | The nine checks, with drill-through |

**Design:** IBM Plex Sans throughout, **IBM Plex Mono with tabular numerals for every figure** — in a
compensation tool the numerals *are* the display type, so columns of money align on the decimal
without any extra work. Light and dark themes, both driven entirely from CSS variables; no component
contains a raw colour value. Every control renders at small size, because one default-size control
next to small ones reads as a mistake rather than a detail.

---

## 11. What makes the numbers trustworthy

These hold everywhere, and each exists because breaking it fails *silently*:

1. **Money is `NUMERIC(15,2)` in the database and `BigDecimal` in Java** — never a float, never a
   JavaScript number doing arithmetic. The browser formats; it does not calculate. Every figure on
   screen was computed server-side.
2. **A money value never travels without its currency.**
3. **Compensation is insert-only**, enforced by a database exclusion constraint.
4. **Normalisation uses a pinned rate**, so a report is reproducible.
5. **Every native query names its schema** — a build-time test fails on an unqualified name, because
   that particular mistake reaches production intact.
6. **Demographics are isolated** — aggregate, cohort ≥ 5, or not at all.
7. **Every read of individual pay data is audited.**
8. **Effective dates are calendar dates, event times are UTC instants** — storing an effective date
   as a timestamp puts raises a day early for half the organisation.

---

## 12. Deliberately not built

The exclusions are part of the design, not gaps in it:

| Excluded | Why |
|---|---|
| Payroll execution, payslips, tax filing | This is a management and analysis tool. Salary OS is the upstream source of truth that *feeds* payroll, not a replacement for it |
| Benefits, equity grants, time & attendance | Total-rewards modelling is only meaningful once base pay is trustworthy. Equity is the most likely v2 |
| Performance management | v1 accepts a rating as an input on a change proposal and does not try to own the review process |
| Employee self-service | The persona is the HR Manager. Opening pay data to 10,000 people changes the threat model for a use case nobody asked for |
| Multi-tenancy | One organisation. Every table would carry an id that is always the same value, and every query a predicate that can be forgotten |
| SSO / SAML / SCIM | Database-backed auth was specified. SSO fits behind the same session layer later without touching anything else |
| Configurable multi-level approval chains | One approval step covers the process. Configurable workflow engines are where compensation tools go to die |
| Live FX rates | A report whose numbers change between two runs is not a report |
| A free-text "ask anything about pay" assistant | A natural-language layer cannot guarantee the row-level and cohort-size filtering the rest of the product enforces in SQL. v1 ships a saved-question library instead — the same questions, answered by queries that can be audited |

---

## 13. Built on

| Layer | Tech |
|---|---|
| Backend | Spring Boot 4.0.8 (Java 17+), Spring Security 7 |
| Persistence | Spring Data JPA + Hibernate, Flyway migrations |
| Database | PostgreSQL 17 (`btree_gist`, `daterange` exclusion constraints) |
| Passwords | Argon2id |
| Frontend | Next.js 16 App Router, Server Components by default |
| UI | shadcn/ui on Radix, Tailwind v4, TanStack Query + Table, Recharts |
| Tests | JUnit 5 + Testcontainers (real Postgres 17, never H2) · Vitest |

A modular monolith, deliberately. One service, one schema, packages by domain — the scale does not
justify a distributed system, and distributed state is the most expensive thing you can add to a
product whose core promise is *one authoritative number*.

---

## 14. Current state

**58 API endpoints · 15 screens · 4 roles · 10,000 seeded employees · 49,692 ledger periods.**

- Backend: **206 tests, 0 failures** (`./mvnw clean verify`, real Postgres via Testcontainers)
- Frontend: **45 tests, 0 failures**, lint and typecheck clean, production build clean
- Performance: employee list **p95 17–21 ms** on the full 10k against a 400 ms budget, including the
  result count and the market-benchmark lookup
- Reconciliation: payroll cost and headcount match direct SQL exactly, and all three breakdowns sum
  to the overall figure
- Permissions: verified live against all four roles across 17 capabilities, including the two
  deliberate absences

Phases `P0`–`P11` are complete. `P12`–`P14` — compensation cycles with budget pools, scenario
modelling, an in-app notification centre, the regulatory-shaped pay-gap report, a joint pay
assessment workspace, and an outbound applied-changes feed — are specified in `BuildPlan.md` and not
yet built.

**One rough edge:** market-data import has a working, tested endpoint
(`POST /api/market-data/import`, HR Admin only, same dry-run-then-apply contract as every other
importer) but **no tab in the import hub yet** — today it is reachable by API only. Everything
downstream of it, including the `<BandBar>` tick and the mid-vs-market figure in band health, works
against imported data.

**One caveat worth stating plainly:** the seeded bands sit low relative to the seeded salaries, which
is why 6,696 of 9,580 people read as above maximum. The figures reconcile exactly with the database,
so the analytics are correct — but the seed makes the out-of-band screen look alarming for reasons
that are about the fixture, not the organisation.

---

*Scope contract: `requirements-one-pager.md` · Architecture and invariants: `CLAUDE.md` ·
Build tracker: `BuildPlan.md` · Backend and UI specs: `docs/`*
