# Technical-Requirements.md — Salary OS

Companion to `requirements-one-pager.md` (the scope contract) and `CLAUDE.md` (the shared context).
This document is the **specification**: what must be true when the build is finished, expressed as
numbered requirements the `BuildPlan.md` steps reference and the acceptance suite asserts.

---

## 1. Actors

| Actor | Description |
|---|---|
| **HR Manager** | The primary persona. Owns the register, proposes and approves changes, runs insights. |
| **HR Admin** | Superuser for account and data administration. One or two people. |
| **Comp Analyst** | Reads everything, proposes changes, cannot approve. |
| **Auditor** | Read-only, including the audit log. Cannot mutate anything. |
| **Scheduler** | The system itself, applying approved changes on their effective date. |

---

## 2. Functional requirements

### FR-1 · Authentication & accounts
| ID | Requirement |
|---|---|
| FR-1.1 | Email + password login against `salary_schema.users`; Argon2id hashing; no external IdP. |
| FR-1.2 | Session issued as an `HttpOnly` cookie JWT + readable CSRF cookie + rotating refresh cookie with reuse detection (`CLAUDE.md §4`). |
| FR-1.3 | Five consecutive failed logins lock the account for 15 minutes; the response is identical for wrong password, unknown email, and locked account (no account enumeration). |
| FR-1.4 | `GET /api/auth/me` returns the current user's id, name, email, role, and theme preference. |
| FR-1.5 | HR Admin can create, deactivate, and reassign the role of a user. A user cannot change their own role, and the last active HR Admin cannot be deactivated. |
| FR-1.6 | Password reset is admin-issued: HR Admin generates a single-use, 30-minute token; the user sets a new password. No email transport is required in v1 — the token is displayed once to the admin. |

### FR-2 · Employee register
| ID | Requirement |
|---|---|
| FR-2.1 | Hold 10,000+ employees with: employee number (unique), first/last name, work email, department, location, job family, job level, manager, hire date, employment type (`FULL_TIME`/`PART_TIME`/`CONTRACT`), FTE (0.01–1.00), status (`ACTIVE`/`ON_LEAVE`/`TERMINATED`), termination date. |
| FR-2.2 | List view with server-side search (name, employee number, email), filters (department, location, country, level, status, band position), sort, and **keyset pagination**. |
| FR-2.3 | The list shows current base pay, currency, compa-ratio, and the band bar per row. |
| FR-2.4 | Employee detail page: identity, org placement, current compensation with components, full pay history ledger, band context, change history with approvers. |
| FR-2.5 | Create and edit an employee. Editing job level or location **does not** change pay — it flags the employee as `band-mismatched` until a compensation change resolves it. |
| FR-2.6 | Termination sets `status = TERMINATED` and closes the open compensation period on the termination date. Terminated employees are excluded from cost and equity analytics by default, with an explicit toggle to include them. |
| FR-2.7 | CSV export of the current filtered view, streamed, capped at 50,000 rows. |

### FR-3 · Compensation ledger
| ID | Requirement |
|---|---|
| FR-3.1 | Base pay is stored as effective-dated periods: `effective_from`, `effective_to` (null = open), amount, currency, pay frequency (`ANNUAL`/`MONTHLY`/`HOURLY`), annualised amount, normalised annual amount in base currency, and the FX rate id used. |
| FR-3.2 | **Insert-only.** Applying a change closes the open period the day before the new `effective_from` and inserts the new period. No endpoint updates or deletes a compensation record. |
| FR-3.3 | Periods for one employee may not overlap. Enforced by a `daterange` `EXCLUDE` constraint, not only by service code. |
| FR-3.4 | Recurring components alongside base: `BONUS_TARGET` (percent or amount), `HOUSING`, `TRANSPORT`, `OTHER_ALLOWANCE`. Each has its own amount and currency and is included in total target cash. |
| FR-3.5 | A correction creates a new record with `change_reason = CORRECTION` and a mandatory note. The superseded row is retained and marked `superseded_by`. |
| FR-3.6 | Pay history is queryable **as at any date**: "what was this person paid on 2024-06-30" returns one row. |

### FR-4 · Salary bands
| ID | Requirement |
|---|---|
| FR-4.1 | A band is defined per (job level × country), with min / mid / max, currency, and an effective date range. |
| FR-4.2 | Bands are effective-dated like pay — comparing a 2023 salary uses the 2023 band, not today's. |
| FR-4.3 | `compa_ratio = annual_base / band_mid`; `range_penetration = (base − min) / (max − min)`. Both computed server-side, both null (not zero) when no band exists for the combination. |
| FR-4.4 | An employee with no matching band is surfaced as an exception, never silently given a compa-ratio of 1.0. |
| FR-4.5 | HR Manager can create and version a band. Editing an in-force band closes it and opens a successor; it never mutates in place. |
| FR-4.6 | Band import from CSV with a dry-run diff before commit. |

### FR-5 · Compensation changes & approval
| ID | Requirement |
|---|---|
| FR-5.1 | Lifecycle `DRAFT → PENDING → APPROVED → APPLIED`, with `REJECTED` and discard, per `CLAUDE.md §8`. |
| FR-5.2 | A proposal carries: employee, effective date, new base amount + currency, reason code (`MERIT`, `PROMOTION`, `MARKET_ADJUSTMENT`, `ROLE_CHANGE`, `LOCATION_CHANGE`, `CORRECTION`, `DEMOTION`), optional performance rating, and a note (mandatory for `CORRECTION` and for anything landing outside band). |
| FR-5.3 | The proposal screen shows, before submission: current pay, proposed pay, absolute and percent delta, current and resulting compa-ratio, resulting band position, and the peer distribution for that level and location. |
| FR-5.4 | A proposal that lands outside the band is allowed but requires the note and is flagged in the approval queue. |
| FR-5.5 | The proposer cannot approve their own proposal. |
| FR-5.6 | One non-terminal change per employee at a time; a second attempt returns 409 with the id of the open one. |
| FR-5.7 | Approved changes are applied by a daily job at 02:00 UTC when their effective date arrives, plus an idempotent manual "Apply due changes" action. Applying twice must not create two records. |
| FR-5.8 | Bulk merit cycle: upload a CSV of (employee number, new amount, reason, note), validated row by row, producing one proposal per valid row and a downloadable error report for the rest. Partial success is the expected outcome and is reported as counts. |

### FR-6 · Insights — the seven questions
Each maps to a saved question in the UI and an endpoint in `/api/analytics`.

| ID | Question | Output |
|---|---|---|
| FR-6.1 | What do we spend on base pay? | Total annualised base, normalised, broken down by country, department, and level; headcount and average alongside every total. |
| FR-6.2 | Who is paid outside their band? | List of employees below min or above max, the gap amount, and the total cost to bring everyone to minimum. |
| FR-6.3 | What is the compa-ratio distribution? | Histogram and quartiles, filterable by department, level, country; median compa-ratio per group. |
| FR-6.4 | Do groups doing the same work get paid differently? | Cohort = (job level × country). Per cohort: median pay per group, difference, and cohort size. **Cohorts smaller than 5 in any group are suppressed and counted as "not shown: N cohorts below threshold".** Unadjusted and level-adjusted figures reported separately, and labelled as such. |
| FR-6.5 | What did the last cycle cost? | Total increase spend for a date range, by reason code; average and median increase percent; budget burn against an entered budget. |
| FR-6.6 | How does this compare to peers? | For one employee: their position against the distribution of the same level and country — p25 / median / p75 and their percentile. |
| FR-6.7 | What is this person's pay history? | The ledger: every period, every change, delta, reason, note, proposer, approver, date. |

| FR-6.8 | Every analytics response states its **as-at date**, the **FX rate month** used, the **population** (headcount included), and any **exclusions applied**. A number without its basis is not shippable. |

### FR-7 · Audit
| ID | Requirement |
|---|---|
| FR-7.1 | Every write records actor, action, entity type, entity id, before/after JSON, and timestamp. |
| FR-7.2 | Every **read** of individual pay data records actor, the employee ids returned (or the filter, for list reads), and timestamp. |
| FR-7.3 | The audit log is append-only — no update or delete endpoint exists, and the application's database role lacks `UPDATE`/`DELETE` on the table. |
| FR-7.4 | Audit search by actor, entity, action, and date range; CSV export. |

### FR-8 · Seed data
| ID | Requirement |
|---|---|
| FR-8.1 | A seed profile generates **10,000 employees** across 8 countries, 14 departments, 9 job families, 7 levels. |
| FR-8.2 | Six years of pay history: 3–7 compensation records per employee (~40,000 rows), 72 months of FX rates, ~1,200 changes in various states, 6 user accounts (one per role plus spares). |
| FR-8.3 | **Reproducible**: a fixed RNG seed produces byte-identical data. Two runs of the seed on empty databases yield the same figures, so a screenshot in a doc stays correct. |
| FR-8.4 | Deliberate anomalies so every screen has something real to show: ~1.8% below band minimum, ~0.6% above maximum, ~40 employees with no matching band, one department carrying a visible adjusted pay gap, and a spread of compa-ratios wide enough to make the histogram meaningful. |
| FR-8.5 | Completes in under 90 seconds against Neon. |

---

## 3. Non-functional requirements

| ID | Requirement | Target |
|---|---|---|
| NFR-1 | Employee list, filtered, 10k rows in table | p95 < 400 ms server time |
| NFR-2 | Any analytics endpoint | p95 < 1.5 s |
| NFR-3 | Employee detail with full history | p95 < 300 ms |
| NFR-4 | First contentful paint on the list route | < 1.5 s on a cold cache |
| NFR-5 | No endpoint returns more than 200 rows without pagination; no endpoint ever returns all 10,000 employees to the browser |
| NFR-6 | All aggregation happens in SQL. The browser never sums money |
| NFR-7 | Accessibility: WCAG 2.1 AA — 4.5:1 text contrast in both themes, visible keyboard focus on every interactive element, full keyboard operation of the table and dialogs, `prefers-reduced-motion` respected |
| NFR-8 | Responsive to 375px. The table degrades to a card list, not a horizontal scroll of 11 columns |
| NFR-9 | Backend line coverage ≥ 70%, and 100% of the invariants in `CLAUDE.md §6` covered by a test that fails when the invariant is broken |
| NFR-10 | Zero `any` in `salary-web/src`; build fails on a type error or a lint error |
| NFR-11 | Connection pool sized for Neon's pooled endpoint (max 10); no query holds a transaction across an HTTP boundary |
| NFR-12 | Every error response is RFC 7807 `ProblemDetail` with a message written for a human, because the UI shows `detail` directly |

---

## 4. Data model

### 4.1 Tables (schema `salary_schema`)

**Identity & access**
- `users` — id, email (unique, citext), full_name, password_hash, role, status, failed_login_count, locked_until, theme_preference, created_at, updated_at
- `user_sessions` — id, user_id, jti, refresh_token_hash, family_id, issued_at, expires_at, revoked_at, user_agent, ip
- `password_reset_tokens` — id, user_id, token_hash, expires_at, used_at

**Organisation reference data**
- `countries` — code (PK, ISO-3166-1 alpha-2), name, default_currency
- `locations` — id, country_code, city, name, is_active
- `departments` — id, name, code, parent_id, cost_centre
- `job_families` — id, name, code
- `job_levels` — id, job_family_id, level_code (`L1`…`L7`), title, sort_order

**People**
- `employees` — id, employee_number (unique), first_name, last_name, work_email (unique), department_id, location_id, job_family_id, job_level_id, manager_id (self-FK), hire_date, employment_type, fte, status, termination_date, created_at, updated_at
- `employee_demographics` — employee_id (PK/FK), gender, date_of_birth, ethnicity_code — **restricted; see `CLAUDE.md §6.6`**

**Compensation**
- `salary_bands` — id, job_level_id, country_code, currency, min_amount, mid_amount, max_amount, effective_from, effective_to, created_by, note
- `compensation_records` — id, employee_id, effective_from, effective_to, validity (`daterange`, generated), base_amount, currency, pay_frequency, annual_base_amount, normalized_annual_base, base_currency, fx_rate_id, band_id, compa_ratio, range_penetration, change_id, change_reason, superseded_by, created_by, created_at
- `compensation_components` — id, compensation_record_id, component_type, amount, currency, percent_of_base, is_recurring
- `compensation_changes` — id, employee_id, status, effective_date, current_base_amount, new_base_amount, currency, change_reason, performance_rating, note, proposed_by, proposed_at, decided_by, decided_at, decision_note, applied_at, applied_record_id
- `fx_rates` — id, rate_month (`date`, first of month), base_currency, quote_currency, rate — unique on (rate_month, base, quote)

**Cross-cutting**
- `audit_events` — id, occurred_at, actor_user_id, actor_role, action, entity_type, entity_id, before_json, after_json, request_id, ip
- `employee_current_comp` — projection: employee_id (PK), compensation_record_id, base_amount, currency, annual_base_amount, normalized_annual_base, band_id, compa_ratio, range_penetration, band_status (`IN_BAND`/`BELOW_MIN`/`ABOVE_MAX`/`NO_BAND`), refreshed_at

### 4.2 Key constraints

```sql
-- one open-ended period, no overlaps, ever
ALTER TABLE salary_schema.compensation_records
  ADD CONSTRAINT comp_no_overlap
  EXCLUDE USING gist (employee_id WITH =, validity WITH &&);   -- requires btree_gist

-- money is never negative and never currency-less
ALTER TABLE salary_schema.compensation_records
  ADD CONSTRAINT comp_amount_positive CHECK (base_amount > 0),
  ADD CONSTRAINT comp_currency_iso    CHECK (currency ~ '^[A-Z]{3}$');

-- a band is coherent
ALTER TABLE salary_schema.salary_bands
  ADD CONSTRAINT band_ordered CHECK (min_amount <= mid_amount AND mid_amount <= max_amount);

-- one non-terminal change per employee
CREATE UNIQUE INDEX one_open_change_per_employee
  ON salary_schema.compensation_changes (employee_id)
  WHERE status IN ('DRAFT','PENDING','APPROVED');
```

### 4.3 Indexes that matter

`employees (department_id, status)`, `employees (location_id, status)`, `employees (job_level_id)`,
`employees (last_name, id)` for keyset pagination, a `pg_trgm` GIN index on
`lower(first_name || ' ' || last_name)` for search, `compensation_records (employee_id,
effective_from DESC)`, `employee_current_comp (band_status)`, `audit_events (occurred_at DESC)`,
`audit_events (entity_type, entity_id)`.

### 4.4 `employee_current_comp` — why a table and not a view

The list screen filters and sorts on compa-ratio and band status for 10,000 rows. Computing that per
request means joining bands and FX per row on every keystroke of a search box. It is maintained
transactionally by the same service call that inserts a compensation record — **not** by a trigger,
so it is visible in the code path that changes it — with a full rebuild command
(`POST /api/admin/rebuild-projection`) for repair. It is a cache; the ledger is the truth. Any
disagreement between them is a bug in the writer, and `ProjectionConsistencyTest` re-derives the
whole projection from the ledger and asserts equality.

---

## 5. API surface

All under `/api`, JSON, cookie-authenticated, `ProblemDetail` on error.

**Auth** — `POST /auth/login` · `POST /auth/logout` · `POST /auth/refresh` · `GET /auth/me` ·
`PATCH /auth/me/preferences`

**Employees** — `GET /employees` (search, filters, keyset cursor) · `GET /employees/{id}` ·
`POST /employees` · `PATCH /employees/{id}` · `POST /employees/{id}/terminate` ·
`GET /employees/{id}/compensation` (full ledger) · `GET /employees/{id}/compensation/as-at?date=` ·
`GET /employees/{id}/peers` (FR-6.6) · `GET /employees/export` (CSV stream)

**Bands** — `GET /bands` · `POST /bands` · `PATCH /bands/{id}` (versions, never mutates) ·
`POST /bands/import` (`?dryRun=true` returns the diff)

**Changes** — `GET /changes` (status, employee, date filters) · `POST /changes` ·
`PATCH /changes/{id}` (draft only) · `POST /changes/{id}/submit` · `POST /changes/{id}/approve` ·
`POST /changes/{id}/reject` · `DELETE /changes/{id}` (draft only) ·
`POST /changes/bulk-upload` · `POST /changes/apply-due` (idempotent)

**Analytics** — `GET /analytics/payroll-cost` · `GET /analytics/out-of-band` ·
`GET /analytics/compa-ratio-distribution` · `GET /analytics/pay-gap` ·
`GET /analytics/increase-cycle` · `GET /analytics/headcount`

**Reference** — `GET /reference/departments|locations|countries|job-families|job-levels|currencies`

**Admin** — `GET|POST|PATCH /admin/users` · `POST /admin/users/{id}/reset-token` ·
`GET /admin/audit` · `POST /admin/rebuild-projection` · `GET|POST /admin/fx-rates`

### 5.1 Shared response shapes

```jsonc
// Money — never a bare number, anywhere
{ "amount": "142500.00", "currency": "GBP" }

// Analytics envelope — FR-6.8; every analytics response carries its basis
{
  "asAtDate": "2026-08-20",
  "fxRateMonth": "2026-08-01",
  "baseCurrency": "USD",
  "population": { "headcount": 9847, "excluded": { "terminated": 153, "noBand": 40 } },
  "suppressedCohorts": 12,
  "data": [ /* … */ ]
}
```

---

## 6. Acceptance criteria

The build is done when all of these pass against seeded data:

1. Log in as each of the four roles; each sees exactly the nav and the permissions in `CLAUDE.md §7`,
   and a forbidden endpoint returns 403 rather than an empty page.
2. The employee list loads 10,000 seeded employees, filters by department + country + band status,
   sorts by compa-ratio, and pages to the end without a duplicate or a skipped row.
3. Opening an employee shows their full ledger; picking a past date returns the salary in force then.
4. Proposing a raise shows the delta, the resulting compa-ratio, and the peer distribution before
   submission; a second proposal for the same employee is refused with a 409 naming the open one.
5. A proposer cannot approve their own change. An approver can. Applying a due change writes exactly
   one compensation record and closes the previous period on the day before.
6. Running "apply due changes" twice writes nothing the second time.
7. Attempting an overlapping compensation period fails at the database, not just in the service.
8. All seven insight questions render, each showing its as-at date, FX month, and population.
9. The pay-gap screen suppresses every cohort under five and reports how many it suppressed.
10. No response anywhere in the app contains a demographic field attached to a person — asserted by
    `DemographicsIsolationTest` over every DTO.
11. Both themes pass a contrast audit; the app is usable at 375px and by keyboard alone.
12. The seed runs twice from empty and produces identical totals.
