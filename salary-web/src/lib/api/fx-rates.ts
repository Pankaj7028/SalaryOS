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

export type FxRateAdmin = {
  rates: FxRate[];
  missing: MissingFxRateMonth[];
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
