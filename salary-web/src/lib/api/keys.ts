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
  countries: () => ["reference", "countries"] as const,
};

export const employeeKeys = {
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
};
