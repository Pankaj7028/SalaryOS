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

/**
 * `total` and `average` are basis-neutral on purpose (renamed from `totalAnnualBase` in P10.7).
 * On the `TOTAL_TARGET_CASH` basis the figure is base plus recurring components, and a field still
 * calling itself "base" would be describing something it is not. The response's own `basis` says
 * what is being totalled.
 */
export type PayrollCostGroup = {
  key: string;
  label: string;
  headcount: number;
  total: Money;
  average: Money;
};

/** What a payroll-cost figure counts (FR-3.4 / P10.6). */
export type AnalyticsBasis = "BASE" | "TOTAL_TARGET_CASH";

/**
 * The FX span this population was normalised over (P10.1). A population spanning many employees
 * has no single governing rate month — every figure is pinned to whichever rate was in force when
 * that employee's own record was written — so the response reports a span, not a scalar.
 */
export type FxBasis = {
  earliestRateMonth: string | null;
  latestRateMonth: string | null;
  distinctRateMonths: number;
  currencies: string[];
};

export type PayrollCostResponse = {
  asAtDate: string;
  baseCurrency: string;
  basis: AnalyticsBasis;
  population: AnalyticsPopulation;
  fxBasis: FxBasis;
  overall: PayrollCostGroup;
  byCountry: PayrollCostGroup[];
  byDepartment: PayrollCostGroup[];
  byLevel: PayrollCostGroup[];
};

// ---- data health (FR-8.4 / P11.1-P11.2) --------------------------------------------------------

export type DataHealthSeverity = "CRITICAL" | "WARNING" | "INFO";

/**
 * One named data-quality check and how many rows currently fail it.
 *
 * `filter` is the employee-list query string that reproduces the failing rows where one of the
 * list's own filters expresses the check. Where none does, the drill-through uses
 * `?dataHealthCheck=<key>` instead — a parameter that names the check rather than describing a
 * condition, so `/employees` does not grow seven filters that serve one screen. Either way the
 * drill-through is the ordinary employee list, so it inherits that endpoint's RBAC and its audit
 * trail rather than needing its own.
 */
export type DataHealthCheck = {
  key: string;
  label: string;
  explanation: string;
  severity: DataHealthSeverity;
  count: number;
  filter: string | null;
};

export type DataHealthResponse = {
  asAtDate: string;
  totalEmployees: number;
  failingChecks: number;
  checks: DataHealthCheck[];
};

export async function fetchDataHealth(): Promise<DataHealthResponse> {
  const response = await apiFetch("/api/analytics/data-health");
  return (await response.json()) as DataHealthResponse;
}

/** The employee-list URL that shows the people failing a check. */
export function dataHealthDrillThroughUrl(check: DataHealthCheck): string {
  return `/employees?${check.filter ?? `dataHealthCheck=${encodeURIComponent(check.key)}`}`;
}

// ---- band health (F9 / P11.3-P11.4) ------------------------------------------------------------

/**
 * One in-force band, judged rather than merely stored (P11.3).
 *
 * `midpointProgression` and `gapToPreviousLevel` are relative to the level below *within the same
 * job family and country* — `job_levels` is unique per (family, level code), so "the level below"
 * means nothing across families. Both are null on the lowest level of a family, which has no
 * previous level to progress from.
 */
export type BandHealthRow = {
  bandId: string;
  jobFamily: string;
  levelCode: string;
  levelTitle: string;
  countryCode: string;
  countryName: string;
  min: Money;
  mid: Money;
  max: Money;
  /** `max / min - 1`. How wide the band is, as a fraction. */
  rangeSpread: string;
  /** This midpoint over the previous level's, minus one. Null on the lowest level. */
  midpointProgression: string | null;
  /** True when this band's minimum sits above the previous level's maximum — a promotion cliff. */
  gapToPreviousLevel: boolean;
  incumbents: number;
  medianCompaRatio: string | null;
  monthsSinceVersioned: number | null;
};

export type BandHealthResponse = {
  asAtDate: string;
  inForceBands: number;
  bandsWithNoIncumbents: number;
  bandsWithGapToPreviousLevel: number;
  staleBands: number;
  /** Not a request parameter — it states how fast pay moves, not something a caller should tune. */
  staleAfterMonths: number;
  rows: BandHealthRow[];
};

export async function fetchBandHealth(): Promise<BandHealthResponse> {
  const response = await apiFetch("/api/analytics/band-health");
  return (await response.json()) as BandHealthResponse;
}

export type HeadcountGroup = { key: string; label: string; headcount: number };

export type HeadcountResponse = {
  asAtDate: string;
  population: AnalyticsPopulation;
  byCountry: HeadcountGroup[];
  byDepartment: HeadcountGroup[];
  byLevel: HeadcountGroup[];
  byStatus: HeadcountGroup[];
};

export async function fetchPayrollCost(basis?: AnalyticsBasis): Promise<PayrollCostResponse> {
  // BASE is the server's own default; omitting it keeps the request identical to every call site
  // that predates the basis parameter.
  const query = basis && basis !== "BASE" ? `?basis=${basis}` : "";
  const response = await apiFetch(`/api/analytics/payroll-cost${query}`);
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
