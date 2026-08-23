/**
 * Every TanStack Query cache key in the app comes from here (CLAUDE.md §9) — a
 * feature never writes `["auth", "me"]` inline, so a key never drifts between
 * the query that reads it and the mutation that invalidates it.
 */
export const authKeys = {
  me: () => ["auth", "me"] as const,
};

export const referenceKeys = {
  departments: () => ["reference", "departments"] as const,
  locations: () => ["reference", "locations"] as const,
  jobLevels: () => ["reference", "jobLevels"] as const,
  jobFamilies: () => ["reference", "jobFamilies"] as const,
  countries: () => ["reference", "countries"] as const,
};

export const employeeKeys = {
  /** Everything employee-shaped — for a mutation that can change many rows at once (P10.5's bulk
   * propose) and has no way to know which pages of which filters are now stale. */
  all: () => ["employees"] as const,
  list: (params: Record<string, string | number | undefined>) => ["employees", "list", params] as const,
  detail: (id: string) => ["employees", "detail", id] as const,
  peers: (id: string) => ["employees", "peers", id] as const,
  compensationHistory: (id: string) => ["employees", "compensationHistory", id] as const,
};

export const bandKeys = {
  list: () => ["bands", "list"] as const,
};

export const changeKeys = {
  impactPreview: (params: Record<string, string | undefined>) => ["changes", "impactPreview", params] as const,
  list: (status: string) => ["changes", "list", status] as const,
};

export const userKeys = {
  list: () => ["users", "list"] as const,
};

export const auditKeys = {
  search: (filters: Record<string, string | undefined>) => ["audit", "search", filters] as const,
};

export const fxRateKeys = {
  admin: () => ["fxRates", "admin"] as const,
};

/** One list for the whole app, not one per route — the picker filters it by route client-side. */
export const savedViewKeys = {
  list: () => ["savedViews", "list"] as const,
};

export const analyticsKeys = {
  /** Keyed by basis (P10.7): BASE and TOTAL_TARGET_CASH are two different answers to the same
   * question, and one must never be served from the other's cache entry. */
  dataHealth: () => ["analytics", "dataHealth"] as const,
  payrollCost: (basis: string = "BASE") => ["analytics", "payrollCost", basis] as const,
  headcount: () => ["analytics", "headcount"] as const,
  outOfBand: () => ["analytics", "outOfBand"] as const,
  compaRatioDistribution: (params: Record<string, string | undefined>) =>
    ["analytics", "compaRatioDistribution", params] as const,
  payGap: () => ["analytics", "payGap"] as const,
  increaseCycle: (params: Record<string, string | undefined>) => ["analytics", "increaseCycle", params] as const,
};
