import { apiFetch } from "./client";

/** Not a {@code Money} value (CLAUDE.md §6.2) — an exchange rate, never rendered via `<Money>`. */
export type FxRate = {
  id: string;
  rateMonth: string;
  baseCurrency: string;
  quoteCurrency: string;
  rate: string;
};

export type MissingFxRateMonth = {
  baseCurrency: string;
  quoteCurrency: string;
  rateMonth: string;
};

/**
 * One (currency, month) square of the coverage matrix. `covered: false` is always a statement about
 * writes *going forward* — a comp record already written pinned its own rate (CLAUDE.md §6.4), so an
 * uncovered past month cannot invalidate a stored figure; it only means a record dated that month
 * can't be written until a rate exists.
 */
export type FxCoverageCell = {
  month: string;
  covered: boolean;
};

export type FxCoverageRow = {
  currency: string;
  employeeCount: number;
  cells: FxCoverageCell[];
};

/** Rows are the currencies actually in use by `employee_current_comp`, not every known currency. */
export type FxCoverage = {
  months: string[];
  quoteCurrency: string;
  rows: FxCoverageRow[];
};

export type FxRateAdmin = {
  rates: FxRate[];
  missing: MissingFxRateMonth[];
  coverage: FxCoverage;
};

export type CreateFxRateInput = {
  rateMonth: string;
  baseCurrency: string;
  quoteCurrency: string;
  rate: string;
};

export async function fetchFxRateAdmin(): Promise<FxRateAdmin> {
  const response = await apiFetch("/api/admin/fx-rates");
  return (await response.json()) as FxRateAdmin;
}

export async function addFxRate(input: CreateFxRateInput): Promise<FxRate> {
  const response = await apiFetch("/api/admin/fx-rates", { method: "POST", body: JSON.stringify(input) });
  return (await response.json()) as FxRate;
}
