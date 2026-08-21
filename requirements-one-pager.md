# Salary OS — Requirements (one page)

**Product:** Salary OS · **Client:** ACME · **Persona:** HR Manager · **Date:** 2026-08-20 · **Status:** approved for build

---

## Goal

Give ACME's HR team one system of record for what 10,000 employees are paid across multiple
countries, replacing the spreadsheets — and let the HR Manager **answer questions about how the org
pays people** without exporting anything.

Success looks like three things: (1) every salary figure has one authoritative value and a dated
history of how it got there; (2) the seven questions below are answered on screen in under five
seconds each; (3) no salary change is ever made without a recorded reason and approver.

## The questions the product exists to answer

1. What do we spend on base pay — total, and by country, department, and job level?
2. Who is paid **outside their band**, and what would it cost to bring them to minimum?
3. What does the **compa-ratio** distribution look like by department and level?
4. For the same job level in the same location, **do groups get paid differently**? (aggregate only)
5. What did the last increase cycle cost, and how much of the merit budget is left?
6. Before I approve this raise, **how does it compare to peers** in the same band?
7. What is this one person's full pay history — every change, when, why, approved by whom?

## Scope — what we are building

| Area | In v1 |
|---|---|
| **Employee register** | 10,000 employees: identity, department, location, job family + level, manager, hire date, status. Search, filter, keyset-paginated list, CSV export. |
| **Compensation ledger** | Effective-dated base pay per employee. **A salary is never overwritten** — a change closes the current period and opens a new one. Recurring components (bonus target, allowances) alongside base. |
| **Salary bands** | Min / mid / max per job level per country, effective-dated. Compa-ratio and range penetration computed against the band in force on the date being viewed. |
| **Changes & approval** | Propose → approve/reject → apply, with a reason code and a note. One approval step. Bulk merit cycle upload. |
| **Insights** | Payroll cost breakdowns, out-of-band exceptions, compa-ratio distribution, cohort pay-gap analysis (aggregate, minimum cohort of 5), increase-cycle spend. |
| **Multi-currency** | Pay stored in the currency it is paid in; every figure also normalised to USD at a **month-pinned FX rate**, so a report run twice gives the same number. Topbar toggles the whole screen between "as paid" and "normalised". |
| **Access control** | Database-backed accounts, four flat roles (HR Admin, HR Manager, Comp Analyst, Auditor), session cookie + CSRF, full audit log of every read of pay data and every write. |

## Deliberately out of scope — and why

| Excluded | Reasoning |
|---|---|
| **Payroll execution, payslips, tax and statutory filing** | This is a *management and analysis* tool. Disbursement and tax are jurisdiction-specific, regulated, and already owned by ACME's payroll providers. Building it would triple the compliance surface and add nothing to the seven questions. Salary OS is the upstream source of truth that feeds payroll, not a replacement for it. |
| **Benefits, equity/stock grants, time & attendance** | Each is a separate domain with its own lifecycle. Total-rewards modelling is only meaningful once base pay is trustworthy — that has to come first. Equity is the most likely v2. |
| **Performance management and review cycles** | Ratings drive merit budgets, but ratings are a different product with different owners. v1 accepts a rating as an *input field* on a change proposal and does not try to own the review process. |
| **Employee self-service** | The persona is the HR Manager. Opening pay data to 10,000 employees changes the threat model, the audit requirements, and the support load, for a use case nobody asked for. |
| **Multi-tenancy** | One organisation, ACME. Every table would carry an `organization_id` that is always the same value, and every query a predicate that can be forgotten. Single-tenant now; the migration is mechanical if it is ever needed. |
| **SSO / SAML / SCIM** | The brief specifies database-backed auth explicitly. SSO is a two-week integration that can be added behind the same session layer later without touching any other code. |
| **Configurable multi-level approval chains** | One approval step covers the stated process. Configurable workflow engines are where compensation tools go to die — they get built for a hypothetical org chart and then nobody can explain why an approval is stuck. |
| **Live FX rates** | A report whose numbers change between two runs is not a report. Rates are pinned per month and stored; the rate used is shown next to the figure. |
| **A free-text "ask anything about pay" assistant** | Tempting given the goal, but a natural-language layer over salary data cannot guarantee the row-level and cohort-size filtering that the rest of the product enforces in SQL. v1 ships a **saved-question library plus a structured query builder** — the same questions, answered by queries that can be audited. Revisit when the answer path can be constrained. |
| **Localisation of the interface** | The users are one HR team. Employee *data* is multi-country; the *UI* is English. |

## Constraints

Spring Boot 4.0.3 (Java 17+) · Next.js 16 App Router + shadcn/ui · Neon PostgreSQL 17 ·
database-backed auth, no Firebase · a reproducible seed script that generates 10,000 employees with
six years of pay history.

## Non-negotiables

- Money is `NUMERIC`, never a float, and never travels without its currency code.
- Compensation rows are insert-only. Correcting a mistake creates a correcting row.
- Demographic attributes live in a separate table, are never displayed per person, and reach
  analytics only as aggregates over cohorts of five or more.
- Every screen that shows a salary shows the band it sits in.
