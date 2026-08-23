import { apiFetch } from "./client";

/**
 * Data fetchers for the saved-view library (P10.3 backend, CLAUDE.md §9).
 *
 * A saved view carries no data — only a route and the query string that screen already puts in the
 * URL. Replaying one issues the identical request the user could have typed, so it is answered by
 * the endpoint that already enforces their role's limits and the cohort-suppression floor. Nothing
 * in this file parses `queryString`; it is stored verbatim and replayed verbatim, and that is the
 * property that keeps the guardrails in SQL where `requirements-one-pager.md` put them.
 */

export type SavedView = {
  id: string;
  name: string;
  route: string;
  /** Raw `searchParams`, no leading `?`. Empty string is legitimate — "all employees" is a view. */
  queryString: string;
  shared: boolean;
  ownedByMe: boolean;
  ownerName: string;
  createdAt: string;
};

export type SaveViewInput = {
  name: string;
  route: string;
  queryString: string;
  shared: boolean;
};

export async function fetchSavedViews(): Promise<SavedView[]> {
  const response = await apiFetch("/api/saved-views");
  return (await response.json()) as SavedView[];
}

/** Re-saving under a name you already used overwrites that view rather than adding a second one. */
export async function saveView(input: SaveViewInput): Promise<SavedView> {
  const response = await apiFetch("/api/saved-views", {
    method: "POST",
    body: JSON.stringify(input),
  });
  return (await response.json()) as SavedView;
}

/** Owner-only. A non-owner gets 404, not 403 — the server declines to confirm the id is real. */
export async function deleteSavedView(id: string): Promise<void> {
  await apiFetch(`/api/saved-views/${id}`, { method: "DELETE" });
}
