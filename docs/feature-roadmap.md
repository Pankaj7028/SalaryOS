# feature-roadmap.md — post-v1 product analysis

**Written 2026-08-23** against the repo at `c73ef45`. **Not binding.** `requirements-one-pager.md`
is still the scope contract and `BuildPlan.md` is still the build tracker — this file is the
*reasoning* behind the `P10`–`P14` steps that were appended to `BuildPlan.md` on the same date.
Read it when you want to know **why** one of those steps exists; read `BuildPlan.md` to know what
to build.

Full narrative version (with the market tables and sources):
<https://claude.ai/code/artifact/1ce6e56d-60ac-48ab-9bce-c4b22c5a031a>

---

## 1. The finding

Every FR in `Technical-Requirements.md` is covered and every `BuildPlan.md` step is `[x]`. That is
real. It is also the trap: **the FR list was written to define v1, not to define a compensation
product.** The gaps below are not missing requirements — they are depth the requirement list never
asked about.

| Domain | FR coverage | Depth gap |
|---|---|---|
| Auth & users (FR-1) | 6/6 | No delegation / out-of-office |
| Employees (FR-2) | 7/7 | No result count, page jump, saved views, bulk select |
| Comp ledger (FR-3) | 6/6 | Components stored and displayed, **read by no analytic** |
| Bands (FR-4) | 6/6 | Nothing evaluates whether the band *structure* is coherent |
| Changes (FR-5) | 8/8 | No cycle object, no budget pool, no queue aging, no notifications |
| Insights (FR-6) | 7½/8 | FX basis unresolved by design; pay gap not in the 2026 regulatory shape |
| Audit (FR-7) | 4/4 | No retention policy, no read-anomaly view |
| Seed (FR-8) | 5/5 | Nothing validates a *real* import the way the seed validates itself |

**Three things v1 does better than most of the category**, worth protecting in any future change:
the ledger is insert-only *below the service* (`daterange` `EXCLUDE` + `btree_gist`, not service
code alone); reports are reproducible because FX is month-pinned with the rate id stored per record;
and demographic isolation is enforced by a build-failing test, not a policy document.

---

## 2. Market read (2026)

Buyers' guides converge on six capabilities: benchmarking data, salary-band management,
review-cycle workflow, pay-equity analysis, total-rewards communication, and bidirectional
HRIS/ATS/payroll integration. Salary OS is strong on two, absent on three, and should not chase one.

- **Do not chase benchmark data.** It is a data business (Pave, Ravio and Figures each spent years
  building contributor networks; Barley licenses from Mercer instead). A single-tenant tool for one
  company has no contributor network and never will. Build the **import seam** instead — `P11.5`.
- **The cycle is the category's central object.** Pave, Barley and the enterprise suites all make
  the review cycle a first-class thing with budget pools and progress. Salary OS has no cycle
  entity at all — `P12.1`.
- **Compliance is now a priced feature, not a report.** Figures positions explicitly on the EU Pay
  Transparency Directive from €2,500/yr.

### The regulatory driver

Strongest single signal, and Salary OS has an unusual head start because it already holds the
inputs (job level × country ≈ category of equal value, components ≈ variable pay, quartiles already
computed, cohort suppression already enforced).

- Transposition deadline was **7 June 2026**; only 4 of 27 member states met it, so national rules
  land through 2026–27 and employers prepare against the Directive itself.
- Employers with **150+ workers** file first in **June 2027, covering CY2026** — the year this
  ledger is recording right now.
- Reporting breaks down **by category of workers performing work of equal value**, not one
  company-wide figure.
- A gap **≥5% in any category**, neither justified nor remedied, triggers a mandatory **joint pay
  assessment**.
- Penalties €250–€1,500 per violation; €1,000–€5,000 for a false or incomplete gap report.

What is missing here is the *shape* of the output and the assessment workflow — `P13`.

---

## 3. The fifteen proposals → where they landed

`F<n>` ids are referenced from the `BuildPlan.md` step text so the two files stay navigable.

| id | Proposal | Scope verdict | Steps |
|---|---|---|---|
| F1 | Saved questions + structured query builder | **Unshipped contract line** | `P10.3`–`P10.4` |
| F2 | Total target cash as a reporting basis | In scope (FR-3.4) | `P10.6`–`P10.7` |
| F3 | `FxBasis` block replacing scalar `fxRateMonth` | In scope (FR-6.8) | `P10.1`–`P10.2` |
| F4 | Result count, page jump, bulk select | In scope (FR-2.2) | `P10.5` |
| F5 | Compensation cycle as a first-class object | In scope (FR-5.8 / 6.5) | `P12.1`–`P12.4` |
| F6 | Scenario modelling that lands as drafts | In scope (extends FR-6.2) | `P12.8`–`P12.10` |
| F7 | Approval-queue depth | In scope — *not* multi-level chains | `P12.5`–`P12.7` |
| F8 | In-app notification centre | In scope — no mail transport | `P12.11`–`P12.12` |
| F9 | Band health diagnostics | In scope (FR-4 depth) | `P11.3`–`P11.4` |
| F10 | Market-data import seam | **Reframed** — seam, not dataset | `P11.5`–`P11.6` |
| F11 | Data-health console | In scope (supports the migration goal) | `P11.1`–`P11.2` |
| F12 | Pay gap in the regulatory shape | In scope (FR-6.4 depth) | `P13.1`–`P13.4` |
| F13 | Joint pay assessment workspace | In scope — aggregate only | `P13.5`–`P13.6` |
| F14 | Pay-information request packs | **Reframed** — document, not portal | `P13.7` |
| F15 | Outbound applied-changes feed + manager dimension | In scope by positioning | `P14.1`–`P14.3` |

### The two reframes, in full

Both collide with an exclusion and were narrowed rather than argued past.

**F10 — market data.** Benchmarking is the category's centre of gravity and Salary OS cannot
compete there. So: `market_data_points` (source, job level, country, currency, p25/p50/p75,
effective month) with the *same* CSV discipline as bands import, and a market tick on `<BandBar>`.
ACME loads the survey it already buys. The feature is "compare band to market", not "we have data".

**F14 — worker information right.** The Directive gives workers the right to request their own pay
level and the average levels by sex for their category. The obvious answer is a portal, which
`requirements-one-pager.md` excludes with good reasoning (opening pay data to 10,000 people changes
the threat model, the audit requirements and the support load). The reframe: an **HR Manager
workflow that produces a document** — print-ready, from the employee detail page, aggregates under
the same ≥5 suppression, audited as a pay-data read. No new auth surface, no new role, no employee
login.

---

## 4. Exclusion-list stance

All eleven exclusions should be **kept**. Recorded here so the next session does not re-litigate
them. Anything that changes needs an ADR in `docs/adr/`.

| Excluded | Pressure | Stance |
|---|---|---|
| Equity & stock grants | High | Hold. One-pager already calls it "the most likely v2". **Build `P10.6`'s `basis` param as the seam it plugs into.** |
| Employee self-service | High | Keep. `P13.7` satisfies the statutory obligation without a portal. |
| Free-text pay assistant | Medium | Keep. The stated revisit condition is "when the answer path can be constrained" — `P10.3`–`P10.4` *create* that constraint. A later narrow version would map language to **query-builder state, never SQL**. |
| Benefits / total rewards | Medium | Hold. Only meaningful once base pay is trustworthy, which is what `P11` delivers. |
| SSO / SAML / SCIM | Medium | Hold. Assessment is sound — sits behind the existing session layer. Urgent only if users outgrow one HR team. |
| Payroll execution | Low | Keep permanently. But its own wording ("upstream source of truth that feeds payroll") creates the `P14` obligation. |
| Multi-level approval chains | Low | Keep. `P12.5`–`P12.7` add depth without a second step. Same "warn, don't block" logic governs `P12.4`'s budget pools. |
| Live FX rates | Low | Keep — a differentiator, not a limitation. `P10.1` makes the pinned basis visible. |
| Multi-tenancy · Performance mgmt · UI localisation | Low | Keep all three. |

---

## 5. Sequencing rationale

| Phase | Steps | Why here |
|---|---|---|
| `P10` Close v1 | 7 | `P10.1` clears the only open acceptance question, so v1 can be signed off. `P10.6` lands early because `P13.2` depends on the component join. |
| `P11` Trust the data | 6 | Read-only, no migration, cannot break the ledger. Best value-to-effort available. `P11.1` is the precondition for trusting anything `P12` produces. |
| `P12` Act, don't just report | 12 | Largest and riskiest — `P12.1` changes the shape of `compensation_changes`, and every lifecycle test lives on that table. |
| `P13` Regulation | 7 | Needs `P10.6` (components) and `P12.8` (scenarios). Timed against a June 2027 first filing on CY2026 data. |
| `P14` Downstream | 3 | Independent of everything above; pull forward if payroll hand-off becomes urgent. |

**If only one phase gets built, build `P11`.** Read-only, no migration, and it answers the two
questions the HR Manager asks on their first real day: *can I trust this data* and *are our bands
any good*.

---

## 6. Standing constraints for all of this work

These are the four ways this work can break something silently. Each is already documented in
`CLAUDE.md` or `docs/STATE.md`; they are collected here because `P10`–`P14` hit all four.

1. **Money is computed in SQL and serialised as a string.** `JacksonConfig`'s
   `BigDecimal`-as-string customiser applies to every new figure in `P10.6`, `P11.3`, `P12.8` and
   `P13.1`. A changed response shape means grepping every `lib/api/*.ts` type touching that field —
   a clean `typecheck` proves the types agree with each other, **never** that they agree with the
   server (the post-P8 `string.toFixed is not a function` crash).
2. **Demographics stay aggregate, cohort ≥ 5.** `P13` gets closer to `CLAUDE.md §6.6` than anything
   built so far. `DemographicsIsolationTest` must stay green **without being weakened**. The `P13.5`
   assessment record attaches to a *category*, never a named person.
3. **Every new write path refreshes the projection.** Anything in `P12.9` or `P13.6` that opens or
   closes a `compensation_records` row must call `projector.refresh(employeeId)`, or
   `employee_current_comp` goes silently stale for that employee.
4. **Every salary shows its band.** `P11.6` changes `<BandBar>` — the signature component.
   `docs/salary-management-ui.md §7.1` governs that change and its §12 checklist is not optional.
