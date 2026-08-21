/**
 * Every TanStack Query cache key in the app comes from here (CLAUDE.md §9) — a
 * feature never writes `["auth", "me"]` inline, so a key never drifts between
 * the query that reads it and the mutation that invalidates it.
 */
export const authKeys = {
  me: () => ["auth", "me"] as const,
};
