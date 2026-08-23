/**
 * Params that describe *where you are in the answer* rather than *what the question is*, and so are
 * dropped before a view is saved. A cursor is a position in one result set — replayed a week later
 * it points into a list that has changed underneath it, and the view opens somewhere arbitrary
 * instead of at the top of its own answer.
 *
 * <p>Everything else survives, including `limit` and `sortBy`: how many rows at a time and in what
 * order is part of how someone chose to look at the question, and reproducing it is the point.
 */
const POSITIONAL_PARAMS = ["cursor"];

/**
 * Narrow a screen's live `searchParams` to the part worth persisting. Order is preserved, so a
 * replayed view produces byte-identical URLs to the one that was saved.
 */
export function saveableQueryString(queryString: string): string {
  const params = new URLSearchParams(queryString);
  for (const key of POSITIONAL_PARAMS) params.delete(key);
  return params.toString();
}
