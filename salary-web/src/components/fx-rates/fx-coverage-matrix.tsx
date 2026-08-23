"use client";

import type { FxCoverage, FxCoverageCell, MissingFxRateMonth } from "@/lib/api/fx-rates";

const MONTH_ABBREVIATIONS = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
];

/**
 * Split an ISO month-start (`2026-03-01`) without going through `Date`. Constructing a `Date` from
 * the string parses it as UTC midnight and then renders it in the viewer's zone, which puts every
 * month one label early for anyone west of Greenwich — the same class of bug CLAUDE.md §6.8 rules
 * out on the server by keeping effective dates as `LocalDate`.
 */
function splitMonth(month: string): { year: string; index: number } {
  const [year, monthNumber] = month.split("-");
  return { year, index: Number(monthNumber) - 1 };
}

function monthLabel(month: string): string {
  const { index } = splitMonth(month);
  return MONTH_ABBREVIATIONS[index] ?? month;
}

/**
 * P10.2 — the FX coverage matrix: currency × month, over the currencies **actually in use** by the
 * payroll population, with gaps in `--attention`.
 *
 * <p>It answers a different question from the missing-months chips above it. The chips enumerate
 * every currency that could ever need a rate (every country default plus the base currency); this
 * grid asks the narrower, more urgent question — which months can today's population not be written
 * for. A gap against 1,400 employees is an outage; the same gap in a currency nobody is paid in is
 * noise, and does not get a row here at all.
 */
export function FxCoverageMatrix({
  coverage,
  canManage,
  onAddRate,
}: {
  coverage: FxCoverage;
  canManage: boolean;
  onAddRate: (missing: MissingFxRateMonth) => void;
}) {
  const { months, quoteCurrency, rows } = coverage;
  const gaps = rows.reduce(
    (total, row) => total + row.cells.filter((cell) => !cell.covered).length,
    0,
  );

  if (rows.length === 0) {
    return (
      <p className="type-body-sm text-muted-foreground">
        No employee is on a current compensation record yet, so there is no currency to cover.
      </p>
    );
  }

  return (
    <div className="space-y-3">
      <p className="type-body-sm text-muted-foreground">
        {gaps === 0 ? (
          <>
            Every currency in use has a pinned rate into {quoteCurrency} for all {months.length}{" "}
            months in the window.
          </>
        ) : (
          <>
            <span className="text-attention font-medium">{gaps}</span>{" "}
            {gaps === 1 ? "month is" : "months are"} missing a rate into {quoteCurrency}. A
            compensation record dated an uncovered month cannot be written until its rate is pinned.
          </>
        )}
      </p>

      <div className="border-border overflow-x-auto rounded-lg border">
        <table className="w-full border-collapse">
          <caption className="sr-only">
            FX rate coverage by currency and month, into {quoteCurrency}
          </caption>
          <thead className="bg-muted/40">
            <tr>
              <th
                scope="col"
                className="type-label text-muted-foreground bg-muted/40 sticky left-0 px-3 py-2 text-left"
              >
                Currency
              </th>
              <th scope="col" className="type-label text-muted-foreground px-3 py-2 text-right">
                People
              </th>
              {months.map((month, index) => {
                const { year, index: monthIndex } = splitMonth(month);
                const showsYear = index === 0 || monthIndex === 0;
                return (
                  <th
                    key={month}
                    scope="col"
                    className="text-muted-foreground px-1 py-2 text-center font-normal"
                  >
                    <span className="type-caption block">{monthLabel(month)}</span>
                    <span className="figure-sm block">{showsYear ? year : " "}</span>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.currency} className="border-border border-t">
                <th
                  scope="row"
                  className="figure-sm bg-card sticky left-0 px-3 py-2 text-left font-normal"
                >
                  {row.currency}
                </th>
                <td className="figure-sm numeric px-3 py-2">{row.employeeCount}</td>
                {row.cells.map((cell) => (
                  <td key={cell.month} className="px-1 py-2 text-center">
                    <CoverageSquare
                      cell={cell}
                      currency={row.currency}
                      quoteCurrency={quoteCurrency}
                      canManage={canManage}
                      onAddRate={onAddRate}
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function CoverageSquare({
  cell,
  currency,
  quoteCurrency,
  canManage,
  onAddRate,
}: {
  cell: FxCoverageCell;
  currency: string;
  quoteCurrency: string;
  canManage: boolean;
  onAddRate: (missing: MissingFxRateMonth) => void;
}) {
  const description = `${currency} → ${quoteCurrency}, ${cell.month}: ${
    cell.covered ? "rate pinned" : "no rate pinned"
  }`;

  if (cell.covered) {
    return (
      <span
        title={description}
        className="border-primary/30 bg-primary/15 inline-block size-4 rounded-sm border align-middle"
      >
        <span className="sr-only">{description}</span>
      </span>
    );
  }

  const square = "border-attention/50 bg-attention-subtle inline-block size-4 rounded-sm border align-middle";

  if (!canManage) {
    return (
      <span title={description} className={square}>
        <span className="sr-only">{description}</span>
      </span>
    );
  }

  return (
    <button
      type="button"
      title={`${description} — add`}
      onClick={() =>
        onAddRate({ baseCurrency: currency, quoteCurrency, rateMonth: cell.month })
      }
      className={`${square} focus-visible:ring-ring hover:bg-attention/30 cursor-pointer focus-visible:ring-2 focus-visible:ring-offset-1 focus-visible:outline-none`}
    >
      <span className="sr-only">{description} — add a rate</span>
    </button>
  );
}
