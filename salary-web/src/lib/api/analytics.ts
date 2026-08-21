import type { Money } from "@/lib/money";
import { apiFetch } from "./client";

/**
 * Data fetchers for `/api/analytics/*` (FR-6.1–6.5, FR-6.8). Every response carries its own basis
 * envelope — as-at date, population, and any exclusions applied — never a bare figure (ui doc
 * §8.1/§8.7's "basis line"). Know nothing about React; `analytics-queries.ts` is the sibling hook
 * layer.
 */

export type AnalyticsPopulation = {
  headcount: number;
  excluded: Record<string, number>;
};

// ---- payroll-cost / headcount (FR-6.1) -------------------------------------------------------

export type PayrollCostGroup = {
  key: string;
  label: string;
  headcount: number;
  totalAnnualBase: Money;
  averageAnnualBase: Money;
};

export type PayrollCostResponse = {
  asAtDate: string;
  baseCurrency: string;
  population: AnalyticsPopulation;
  overall: PayrollCostGroup;
  byCountry: PayrollCostGroup[];
  byDepartment: PayrollCostGroup[];
  byLevel: PayrollCostGroup[];
};

export type HeadcountGroup = { key: string; label: string; headcount: number };

export type HeadcountResponse = {
  asAtDate: string;
  population: AnalyticsPopulation;
  byCountry: HeadcountGroup[];
  byDepartment: HeadcountGroup[];
  byLevel: HeadcountGroup[];
  byStatus: HeadcountGroup[];
};

export async function fetchPayrollCost(): Promise<PayrollCostResponse> {
  const response = await apiFetch("/api/analytics/payroll-cost");
  return (await response.json()) as PayrollCostResponse;
}

export async function fetchHeadcount(): Promise<HeadcountResponse> {
  const response = await apiFetch("/api/analytics/headcount");
  return (await response.json()) as HeadcountResponse;
}

// ---- out-of-band (FR-6.2) ---------------------------------------------------------------------

export type OutOfBandRow = {
  employeeId: string;
  employeeFirstName: string;
  employeeLastName: string;
  employeeNumber: string;
  departmentId: string;
  locationId: string;
  jobLevelId: string;
  bandStatus: "BELOW_MIN" | "ABOVE_MAX";
  currentBase: Money;
  bandMin: Money;
  bandMid: Money;
  bandMax: Money;
  gapAmount: Money;
};

export type OutOfBandResponse = {
  asAtDate: string;
  baseCurrency: string;
  population: AnalyticsPopulation;
  belowMinCount: number;
  aboveMaxCount: number;
  totalCostToMinimum: Money;
  rows: OutOfBandRow[];
};

export async function fetchOutOfBand(): Promise<OutOfBandResponse> {
  const response = await apiFetch("/api/analytics/out-of-band");
  return (await response.json()) as OutOfBandResponse;
}

// ---- compa-ratio-distribution (FR-6.3) -----------------------------------------------------

export type CompaRatioHistogramBucket = { bucket: string; count: number };
export type CompaRatioGroupMedian = { key: string; label: string; count: number; medianCompaRatio: string };

export type CompaRatioDistributionResponse = {
  asAtDate: string;
  population: AnalyticsPopulation;
  p25: string;
  median: string;
  p75: string;
  histogram: CompaRatioHistogramBucket[];
  byDepartment: CompaRatioGroupMedian[];
  byLevel: CompaRatioGroupMedian[];
  byCountry: CompaRatioGroupMedian[];
};

export type CompaRatioDistributionParams = {
  departmentId?: string;
  jobLevelId?: string;
  countryCode?: string;
};

export async function fetchCompaRatioDistribution(params: CompaRatioDistributionParams): Promise<CompaRatioDistributionResponse> {
  const search = new URLSearchParams(
    Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined)) as Record<string, string>,
  );
  const qs = search.toString();
  const response = await apiFetch(`/api/analytics/compa-ratio-distribution${qs ? `?${qs}` : ""}`);
  return (await response.json()) as CompaRatioDistributionResponse;
}

// ---- pay-gap (FR-6.4) -------------------------------------------------------------------------

export type PayGapGroupMedian = { group: string; count: number; median: Money };
export type PayGapCohortRow = {
  jobLevelId: string;
  jobLevelLabel: string;
  countryCode: string;
  countryLabel: string;
  groups: PayGapGroupMedian[];
  gapAmount: Money;
  gapPercent: string;
};

export type PayGapResponse = {
  asAtDate: string;
  baseCurrency: string;
  population: AnalyticsPopulation;
  unadjustedGroups: PayGapGroupMedian[];
  unadjustedGapAmount: Money;
  unadjustedGapPercent: string;
  levelAdjustedCohorts: PayGapCohortRow[];
  suppressedCohorts: number;
};

export async function fetchPayGap(): Promise<PayGapResponse> {
  const response = await apiFetch("/api/analytics/pay-gap");
  return (await response.json()) as PayGapResponse;
}

// ---- increase-cycle (FR-6.5) ------------------------------------------------------------------

export type IncreaseCycleReasonRow = {
  reasonCode: string;
  count: number;
  totalIncrease: Money;
  avgIncreasePercent: string;
  medianIncreasePercent: string;
};

export type IncreaseCycleResponse = {
  asAtDate: string;
  fromDate: string;
  toDate: string;
  baseCurrency: string;
  population: AnalyticsPopulation;
  totalIncrease: Money;
  avgIncreasePercent: string;
  medianIncreasePercent: string;
  byReason: IncreaseCycleReasonRow[];
  budget: Money | null;
  budgetBurnPercent: string | null;
};

export type IncreaseCycleParams = { fromDate: string; toDate: string; budget?: string };

export async function fetchIncreaseCycle(params: IncreaseCycleParams): Promise<IncreaseCycleResponse> {
  const search = new URLSearchParams(
    Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined)) as Record<string, string>,
  );
  const response = await apiFetch(`/api/analytics/increase-cycle?${search.toString()}`);
  return (await response.json()) as IncreaseCycleResponse;
}
