# salary-management-ui.md

**BINDING for all `salary-web/` work — existing and new.** Read before writing any UI code; run the
§12 checklist before marking any UI step done. Where this document and a component's default
disagree, this document wins.

---

## 1. Design direction

Salary OS is a **precision instrument for money**, not a dashboard. Its user opens it to answer a
question exactly — "is this person paid correctly", "what will this raise cost", "who is below their
band" — and the interface's job is to make a figure trustworthy at a glance.

Three decisions follow from that, and everything else is downstream of them:

**The numerals are the display type.** There is no decorative serif and no oversized hero stat. Money,
dates, employee numbers, and percentages are set in **IBM Plex Mono** with tabular figures, so a
column of salaries aligns on the decimal without any extra work and a wrong digit is visible in
peripheral vision. The interface face, IBM Plex Sans, stays quiet around them.

**Every salary carries its band.** A number alone is not an answer — £142,500 is generous or
underpaid depending on a band the reader cannot see. So the `<BandBar>` (§7.1) travels with the
figure into every table row, every dialog, every detail panel. This is the signature element, and
the one place the design spends any boldness.

**Rules measure; they do not decorate.** Hairlines separate a figure from its context or one period
from the next. There are no divider flourishes, no card shadows doing the work a border should do,
and no gradient anywhere in the product.

Palette: **zinc neutrals with a deep pine primary**. Pine reads as ledger ink rather than as
"finance app blue", it is not the recruiting-blue of any adjacent product, and it leaves red and
amber entirely free to mean *exception* — which in a compensation tool they must, because
out-of-band is the state the user is hunting for.

---

## 2. Tokens — `salary-web/src/app/theme.css`

Tailwind v4, CSS-first. shadcn's variable names are used verbatim so every shadcn component themes
itself; product-specific tokens are added alongside.

```css
@import "tailwindcss";
@import "tw-animate-css";

/* shadcn's dark variant is rebound to our class name */
@custom-variant dark (&:is(.app-dark *));

/* ---------- LIGHT (.app-light — the default) ---------- */
:root,
.app-light {
  --radius: 0.5rem;                    /* 8px */

  --background:            #FAFAFA;
  --foreground:            #18181B;
  --card:                  #FFFFFF;
  --card-foreground:       #18181B;
  --popover:               #FFFFFF;
  --popover-foreground:    #18181B;

  --primary:               #0B6E4F;    /* pine */
  --primary-foreground:    #FFFFFF;
  --primary-hover:         #0A5C42;
  --primary-active:        #084A35;
  --primary-subtle:        #E6F2ED;    /* active nav pill, selected row */

  --secondary:             #F4F4F5;
  --secondary-foreground:  #27272A;
  --muted:                 #F4F4F5;
  --muted-foreground:      #71717A;
  --accent:                #F4F4F5;
  --accent-foreground:     #18181B;

  --destructive:           #B91C1C;
  --destructive-foreground:#FFFFFF;

  --border:                #E4E4E7;
  --input:                 #D4D4D8;
  --ring:                  #0B6E4F;

  /* product semantics — band status and deltas */
  --positive:              #0B6E4F;    /* increase, in band */
  --positive-subtle:       #E6F2ED;
  --attention:             #A16207;    /* below minimum, expiring band */
  --attention-subtle:      #FEF3C7;
  --critical:              #B91C1C;    /* above maximum, decrease */
  --critical-subtle:       #FEE2E2;
  --neutral-figure:        #52525B;    /* an unchanged number */

  /* chrome */
  --topbar:                #FFFFFF;
  --topbar-foreground:     #18181B;
  --sidebar:               #F4F4F5;
  --sidebar-foreground:    #3F3F46;
  --sidebar-accent:        var(--primary-subtle);
  --sidebar-accent-foreground: var(--primary);
  --sidebar-border:        #E4E4E7;
  --content:               #FAFAFA;

  /* data visualisation — ordered, colour-blind-safe, never re-ordered per chart */
  --chart-1: #0B6E4F;  --chart-2: #0E7490;  --chart-3: #4338CA;
  --chart-4: #86198F;  --chart-5: #9A3412;  --chart-6: #4D7C0F;
}

/* ---------- DARK (.app-dark) ---------- */
.app-dark {
  --background:            #09090B;
  --foreground:            #FAFAFA;
  --card:                  #131316;
  --card-foreground:       #FAFAFA;
  --popover:               #131316;
  --popover-foreground:    #FAFAFA;

  --primary:               #34D399;    /* lifted — see note below */
  --primary-foreground:    #052E22;
  --primary-hover:         #6EE7B7;
  --primary-active:        #6EE7B7;
  --primary-subtle:        #0F2B22;

  --secondary:             #1F1F23;
  --secondary-foreground:  #E4E4E7;
  --muted:                 #1F1F23;
  --muted-foreground:      #A1A1AA;
  --accent:                #1F1F23;
  --accent-foreground:     #FAFAFA;

  --destructive:           #F87171;
  --destructive-foreground:#1A0505;

  --border:                #27272A;
  --input:                 #3F3F46;
  --ring:                  #34D399;

  --positive:              #34D399;
  --positive-subtle:       #0F2B22;
  --attention:             #FBBF24;
  --attention-subtle:      #2A1F05;
  --critical:              #F87171;
  --critical-subtle:       #2A0F0F;
  --neutral-figure:        #A1A1AA;

  --topbar:                #09090B;
  --topbar-foreground:     #FAFAFA;
  --sidebar:               #09090B;
  --sidebar-foreground:    #A1A1AA;
  --sidebar-accent:        var(--primary-subtle);
  --sidebar-accent-foreground: var(--primary);
  --sidebar-border:        #1F1F23;
  --content:               #09090B;

  --chart-1: #34D399;  --chart-2: #22D3EE;  --chart-3: #818CF8;
  --chart-4: #E879F9;  --chart-5: #FB923C;  --chart-6: #A3E635;
}

@theme inline {
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-card: var(--card);
  --color-primary: var(--primary);
  --color-primary-subtle: var(--primary-subtle);
  --color-positive: var(--positive);
  --color-attention: var(--attention);
  --color-critical: var(--critical);
  --color-border: var(--border);
  --color-muted-foreground: var(--muted-foreground);
  /* …one entry per token above… */

  --font-sans: var(--font-plex-sans), ui-sans-serif, system-ui, sans-serif;
  --font-mono: var(--font-plex-mono), ui-monospace, "SFMono-Regular", monospace;

  --radius-sm: calc(var(--radius) - 4px);
  --radius-md: calc(var(--radius) - 2px);
  --radius-lg: var(--radius);
}
```

> **Why the primary lifts in dark.** `#0B6E4F` on `#09090B` is unreadable as text, and in this
> product the primary is read as text at least as often as it is used as a fill — active nav labels,
> links, positive deltas in the ledger, the band marker. So dark uses emerald-400 and flips
> `--primary-foreground` to near-black. Anything you add that uses `--primary` must be legible
> under both, which is what §12's contrast check is for.

**Dark is flat.** Topbar, sidebar, and content all sit at `--background`; only cards lift to
`--card`. Elevation in dark comes from the border and the surface step, never from a shadow — a
shadow on near-black is a smudge.

---

## 3. Typography

Loaded with `next/font/google` in `app/layout.tsx`, exposed as `--font-plex-sans` and
`--font-plex-mono`.

```ts
import { IBM_Plex_Sans, IBM_Plex_Mono } from "next/font/google";

export const plexSans = IBM_Plex_Sans({
  subsets: ["latin"], weight: ["400", "500", "600"],
  variable: "--font-plex-sans", display: "swap",
});
export const plexMono = IBM_Plex_Mono({
  subsets: ["latin"], weight: ["400", "500"],
  variable: "--font-plex-mono", display: "swap",
});
```

### 3.1 Scale

| Role | Size / line-height | Face | Weight | Tracking | Used for |
|---|---|---|---|---|---|
| `figure-xl` | 30 / 36 | Mono | 500 | `-0.02em` | The one headline number on an insight card |
| `figure-lg` | 20 / 28 | Mono | 500 | `-0.01em` | Current salary on the employee detail |
| `figure` | 13 / 18 | Mono | 400 | `0` | Every money cell, date, percent, employee number |
| `figure-sm` | 12 / 16 | Mono | 400 | `0` | Deltas, band endpoints, footnote figures |
| `title` | 20 / 28 | Sans | 600 | `-0.01em` | Page title |
| `section` | 16 / 24 | Sans | 600 | `0` | Card headers, dialog titles |
| `subsection` | 14 / 20 | Sans | 600 | `0` | Grouped field headers |
| `body` | 14 / 20 | Sans | 400 | `0` | Default |
| `body-sm` | 13 / 18 | Sans | 400 | `0` | Table text, helper text |
| `label` | 12 / 16 | Sans | 500 | `0.06em`, uppercase | Field labels, table headers, eyebrows |
| `caption` | 12 / 16 | Sans | 400 | `0` | Timestamps, basis lines, hints |

Base is **14px**, not 16 — this is a table-first application and 16px costs roughly two rows per
screen with no gain in legibility at these line lengths.

### 3.2 Numeral rules (non-negotiable)

```css
.figure, .font-mono, table td.numeric, table th.numeric {
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1, "zero" 1;   /* slashed zero: 0 vs O in employee numbers */
}
```

- **Every money value, percent, ratio, date, and identifier is mono and tabular.** No exceptions —
  a proportional figure in a column of tabular ones is immediately visible as a defect.
- **Money columns are right-aligned; the currency code is a separate muted span**, so the digits
  align even across currencies: `142,500.00` `GBP`.
- **Compa-ratio is always two decimals** (`0.94`), **percent always one** (`+4.2%`), **money always
  two** unless the figure is an aggregate over ≥ 1,000,000, where whole units are used with the
  rounding stated in the caption.
- **A delta always carries its sign** and takes `--positive` / `--critical`; zero takes
  `--neutral-figure` and reads `—`, not `0.0%`.

---

## 4. Spacing, radius, elevation

- **4px grid.** Permitted values only: 4, 8, 12, 16, 20, 24, 32, 40, 48, 64.
- **Radius** `--radius: 8px`; inputs and buttons `6px` (`--radius-md`), badges and chips `4px`.
- **Density:** control height 32px (`sm`), table row 40px, table header 36px, card padding 20px,
  page gutter 24px (16px under 768px).
- **Elevation:** exactly three levels. Flat (border only) for cards and tables; a soft shadow for
  popovers, dropdowns, and command palette; a stronger one for dialogs and sheets. Nothing else
  casts a shadow — not buttons, not table rows, not cards on hover.

---

## 5. Motion

- Durations: 120ms (hover, focus), 180ms (popover, tooltip), 240ms (sidebar collapse, sheet), all on
  `cubic-bezier(.22,1,.36,1)`.
- **Numbers never animate.** No count-ups, no ticking figures. A salary that scrolls into place
  reads as a slot machine, and the one thing this product sells is that its numbers are stable.
- Charts animate once on mount and never on re-filter.
- `@media (prefers-reduced-motion: reduce)` sets every duration to `0.01ms`.

---

## 6. Application shell

```
┌──────────────────────────────────────────────────────────────────────┐
│ .app-topbar   [≡] ▣ Salary OS · ACME    ⌘K search    USD▾  ◐  (av)  │  56px sticky
├───────────────┬──────────────────────────────────────────────────────┤
│ .app-sidebar  │ .app-content                                         │
│  240px        │                                                      │
│               │   Page title                       [ Primary action ]│
│  WORKSPACE    │   ─────────────────────────────────────────────────  │
│   Overview    │                                                      │
│   Employees   │   … content …                                        │
│   Changes     │                                                      │
│               │                                                      │
│  PAY STRUCTURE│                                                      │
│   Salary bands│                                                      │
│   Job levels  │                                                      │
│   Locations   │                                                      │
│               │                                                      │
│  INSIGHTS     │                                                      │
│   Pay analysis│                                                      │
│   Equity      │                                                      │
│   Reports     │                                                      │
│               │                                                      │
│  ADMIN        │                                                      │
│   Users       │                                                      │
│   Import      │                                                      │
│   Audit log   │                                                      │
│  ─────────────│                                                      │
│  ◧ Collapse   │                                                      │
└───────────────┴──────────────────────────────────────────────────────┘
```

### 6.1 Topbar — `.app-topbar`

Full width, sticky, `--topbar` background, 1px `--border` bottom, `backdrop-filter: blur(6px)`.

**Left:** mobile hamburger (< 768px only) → brand: a 28px rounded-square mark (ACME logo, else the
letter mark on `--primary`) + wordmark **"Salary OS"** with the org name beneath it as a `label`.

**Right cluster, in this order:**
1. **Global search** — a 320px trigger reading `Search employees ⌘K`, opening a `Command` palette
   that searches by name, employee number, and email, showing pay and band status inline so the
   most common task (look up one person) never needs the list page.
2. **Currency toggle** — a `Select` reading `USD` or `As paid`. This is a product control, not a
   preference: it switches **every money figure on screen** between the currency each person is
   actually paid in and the normalised base currency. Its state lives in the URL
   (`?ccy=USD|local`) so a shared link shows what the sender saw. When `As paid` is active, any
   aggregate that would sum across currencies renders as "mixed currencies — switch to USD to
   total", never as a wrong number.
3. **Theme menu** — Light / Dark / System.
4. **Avatar** → dropdown: name + email + role badge, Account, Theme, Sign out.

### 6.2 Sidebar — `.app-sidebar`

240px, collapses to a 60px icon rail; state persists in `localStorage`; collapsed rail peeks open on
hover; labels fade via `max-width`/`opacity`. Under 768px it becomes a `Sheet` opened by the
hamburger.

- Groups are the four above, each headed by a `label`-styled caption that disappears when collapsed.
- The active item is a pill: `--primary-subtle` background, `--primary` text, 2px left rule.
- **Groups filter by role** (`NAV_VISIBILITY` in `src/lib/auth/roles.ts`); an empty group disappears
  rather than rendering a header with nothing under it.
- Bottom row: **Collapse** (borderless, icon mirrors when collapsed).

### 6.3 Chrome CSS

```css
.app-shell    { display: flex; flex-direction: column; min-height: 100dvh; }
.app-topbar   { height: 56px; position: sticky; top: 0; z-index: 40;
                background: var(--topbar); border-bottom: 1px solid var(--border);
                backdrop-filter: blur(6px); }
.app-body     { display: flex; flex: 1; min-height: 0; }
.app-sidebar  { width: 240px; position: sticky; top: 56px; height: calc(100dvh - 56px);
                background: var(--sidebar); border-right: 1px solid var(--sidebar-border);
                transition: width 240ms cubic-bezier(.22,1,.36,1); }
.app-shell.collapsed .app-sidebar { width: 60px; }
.app-content  { flex: 1; min-width: 0; background: var(--content); padding: 24px; }
@media (max-width: 767px) {
  .app-sidebar { position: fixed; transform: translateX(-100%); z-index: 50; }
  .app-sidebar.open { transform: none; }
  .app-content { padding: 16px; }
}
```

### 6.4 Page header

Every page: `title` on the left, a one-line `caption` beneath it stating the basis of what is on
screen (as-at date, FX month, population — FR-6.8), and at most one primary action on the right.
Secondary actions go in an overflow menu. Filters sit in a row below the header, never in the
header.

---

## 7. Components

shadcn/ui, `new-york` style, copied into `src/components/ui/` and edited in place. **Every control
that takes a size takes `sm`.** Product components live in `src/components/salary/`.

### 7.1 `<BandBar>` — the signature component

The horizontal band scale. Wherever a salary appears next to a band, this appears with it.

```
below minimum          in band                        above maximum
├──●───┬──────────┤    ├──────┬───●──────┤            ├──────┬──────┤●
min   mid        max   min   mid        max           min   mid    max
```

- A 4px track from band min to max at `--muted`, a 1px tick at mid at `--border`.
- A 3×14px marker at the salary's position: `--positive` in band, `--attention` below min,
  `--critical` above max.
- Out-of-band values sit **outside** the track with a 6px overshoot and the track end capped — never
  clamped to the end, which would render an underpay as a healthy minimum.
- Three widths: `inline` 64px (table cells), `default` 200px (cards, dialogs), `detail` full-width
  with min/mid/max labelled in `figure-sm` (employee page).
- **No band?** The track renders as a dashed `--border` outline with the caption "No band for L4 ·
  Ireland", linking to band setup. It never renders as a full track with a centred marker.
- Accessible name: `"142,500 GBP · compa-ratio 0.94 · in band, 62% through range"`. The bar is
  decorative to a screen reader; the sentence is the content.

### 7.2 `<Money>`

The only way a money value is rendered. Takes `{ amount, currency }` plus the display mode from the
topbar toggle, formats with `Intl.NumberFormat`, applies mono + tabular, right-aligns in a table
context, and renders the currency code as a muted suffix. **It never does arithmetic** — if a
displayed value needs computing, the API returns it.

### 7.3 `<Delta>`

Signed change with sign, colour, and both forms where space allows: `+£6,000 (+4.4%)`. Zero renders
`—`. Never a bare arrow glyph — an arrow without a number is a mood, not a figure.

### 7.4 `<BandStatusBadge>`

`In band` (neutral outline) · `Below min` (`--attention`) · `Above max` (`--critical`) ·
`No band` (dashed outline). Text always present; colour alone never carries the meaning.

### 7.5 Data table

TanStack Table v8 in a shadcn `Table`. Server-side everything — filtering, sorting, pagination.
Sticky header, 40px rows, zebra off, hover `--muted`, selected `--primary-subtle`. Column visibility
in a dropdown, persisted per user. Row click opens the record; the row is a `<Link>` so
middle-click and ⌘-click work.

**No infinite scroll.** Keyset pagination with a page-size control. An HR Manager needs to say
"page 4 of the below-minimum list", and infinite scroll destroys that.

### 7.6 Charts

Recharts, colours from `--chart-1…6` read via `getComputedStyle`, **never hard-coded** — otherwise
the chart is the one element that ignores the theme. Axis and grid at `--border`, labels at
`--muted-foreground` in mono. Every chart has a table equivalent behind a "View as table" toggle:
the histogram is the summary, the table is the evidence, and the evidence is what gets exported.

### 7.7 Feedback

One `<Toaster>` at the root, reached only through `src/lib/notify.ts`
(`success` / `info` / `warning` / `failure(error, summary)`). Never a second `Toaster`, never
`toast()` imported directly into a feature — dwell times and positions drift within a week
otherwise. Dwell: success 3s, info 4s, warning 5s, error 6s.

- `summary` is a past-tense outcome in 2–4 words, no full stop: "Change approved", "Couldn't save".
- `detail` names the record and the specifics; on failure it is the server's `ProblemDetail.detail`,
  and if the server gave no reason, no detail is shown rather than invented filler.
- **Bulk actions emit one toast with a count**: "412 proposals created · 18 rows rejected".
- 401 redirects to sign-in without a toast; 403 and network failures are reported centrally by the
  fetch wrapper, not per screen.
- Field validation is inline next to the field, never a toast. Never both for the same error.

### 7.8 Loading and empty states

- **Skeletons by default**, mirroring the final geometry so nothing shifts. Table skeletons render
  the real column widths and the real row height. No spinners for page content; a spinner is for a
  blocking action, and an inline button spinner for an in-place one.
- **Empty is an invitation, not an apology.** "No employees match these filters — clear the country
  filter to widen the search", with the clearing action as a button. Never "No data available".
- **A zero that is a real answer says so**: the out-of-band screen with nothing to show reads
  "Everyone is inside their band" with the population it checked — a genuinely good result, and it
  must not look like a broken query.
- **Errors state what failed and what to do**: "Couldn't load pay analysis — the report timed out.
  Narrow the date range and try again." No apologies, no "something went wrong".

---

## 8. Screens

### 8.1 Overview (`/`)
Six figures across the top, each a card with one `figure-xl`, a `label`, and a `figure-sm`
comparison: total annualised base, headcount, median compa-ratio, employees outside band, changes
awaiting approval, spend on increases YTD. Below: base pay by country (bar), compa-ratio
distribution (histogram), and the approval queue as a compact table. The basis line under the page
title states as-at date, FX month, and population.

### 8.2 Employees (`/employees`)
Filter row: search, department, location, country, level, status, band status, plus a saved-view
select. Table columns: name + employee number, department, location, level, base pay, currency,
compa-ratio, `<BandBar inline>`, status. Sort on any of them. Bulk select → "Propose changes for
selected". Export CSV of the current filter.

### 8.3 Employee detail (`/employees/[id]`)
Header: name, employee number, role, manager, location, status badge. Then three panels:
- **Current pay** — `figure-lg` base, components listed beneath, `<BandBar detail>` with min/mid/max
  labelled, compa-ratio and range penetration as figures with their formulas in a tooltip.
- **Pay history** — the ledger: a vertical hairline with one dated entry per period, mono dates,
  `<Delta>` per change, reason chip, note, proposer and approver. This is the answer to question 7,
  and it is a *ledger*, not a timeline decoration.
- **Peers** — the distribution for the same level and country: p25 / median / p75 and this person's
  percentile, with the cohort size stated. Suppressed under five.

Primary action: **Propose change**.

### 8.4 Propose change (dialog)
Two columns. Left: effective date, new amount + currency, reason code, performance rating, note.
Right, live as you type: current → proposed with `<Delta>`, resulting compa-ratio, `<BandBar>`
showing the marker move, peer percentile before and after, and the annualised cost. Landing outside
the band turns the note field required and shows why in one line. Submit is disabled until the form
is valid, and the button says **Submit for approval** — which is what happens.

### 8.5 Changes (`/changes`)
Tabs (URL state): Awaiting approval · Approved · Applied · Rejected · Drafts. Each row shows
employee, current → proposed, delta, effective date, reason, proposer, and an out-of-band flag.
Approve/reject inline with a required note on reject. The tab you cannot act on for your role is
visible but its actions are absent — not disabled-with-a-tooltip.

### 8.6 Salary bands (`/bands`)
Grid of level × country, each cell showing min–mid–max and the count of employees in it. Clicking
opens the band with its version history. Creating a new version shows how many employees change
status as a result **before** saving — the most useful number on the screen.

### 8.7 Pay analysis (`/insights/pay`)
The saved-question library: each of FR-6.1, 6.2, 6.3, 6.5 as a card that expands into its full view
with filters. Every result carries its basis line and a "View as table" toggle.

### 8.8 Equity review (`/insights/equity`)
Cohort table: level × country, median per group, difference, cohort size. Cohorts under five are not
rendered and the count of suppressed cohorts is stated at the top, with one line explaining why.
Unadjusted and level-adjusted figures are two separate columns with two separate labels — conflating
them is how a pay-gap number becomes indefensible in a room.

### 8.9 Admin (`/admin/*`)
Users and roles, data import (CSV with dry-run diff), FX rates by month, audit log with filters.

---

## 9. URL state

List, filter, sort, tab, page, and the currency toggle all live in `searchParams`. A screen is a
link. This is what lets the HR Manager send "everyone below band in Germany, in USD" to Finance
without exporting anything, and it is the single most-used affordance in a tool like this.

---

## 10. Accessibility

- 4.5:1 for text and 3:1 for UI boundaries, **in both themes**, verified — not assumed.
- Colour never carries meaning alone: every band status has text, every delta has a sign.
- Visible focus ring (`--ring`, 2px, 2px offset) on everything focusable. Never `outline: none`.
- The table is keyboard-navigable; dialogs trap focus and restore it on close.
- Charts are `aria-hidden` with the table equivalent as the accessible content.
- Live regions announce toast text.

---

## 11. Performance

- Server Components render the shell and the first page of data; only the filter bar, the table
  interactions, and the charts are client islands.
- The table is virtualised above 100 rows.
- Search input is debounced 250ms and the request is aborted on the next keystroke.
- No client-side sum, average, or currency conversion — ever (`CLAUDE.md §6.1`).
- Route-level `loading.tsx` renders the page skeleton, not a spinner.

---

## 12. Merge checklist — run before marking any UI step done

1. No raw hex, `rgb()`, or named colour anywhere outside `theme.css`.
2. No off-scale font size; no spacing value outside the 4px set in §4.
3. Every money, percent, ratio, date, and identifier is mono and tabular.
4. Every salary shown has its band shown, or an explicit "no band" state.
5. Every control that accepts a size is `sm`.
6. Both themes checked visually, and text contrast measured in both.
7. Loading state is a geometry-matching skeleton; empty state names an action; error state names the
   failure and the fix.
8. Filter, sort, tab, page, and currency state are in the URL, and reloading the page restores them.
9. Keyboard-only pass: reach every control, operate the table, open and close the dialog, focus
   returns.
10. 375px pass: no horizontal scroll, table degraded to cards.
11. `prefers-reduced-motion` honoured; no number animates.
12. `npm run lint && npm run typecheck && npm run build` clean, and no `any` added.
