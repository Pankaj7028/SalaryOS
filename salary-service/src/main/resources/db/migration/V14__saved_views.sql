-- V14: saved views (P10.3, FR — requirements-one-pager.md's "saved-question library").
--
-- This is the substitute the scope contract promised in the same sentence that excluded a
-- free-text "ask anything about pay" assistant: "v1 ships a saved-question library plus a
-- structured query builder — the same questions, answered by queries that can be audited."
-- It was never built. Nothing here is a new query capability: CLAUDE.md §9 already requires
-- every list/filter/sort/tab state to live in the URL, so a saved view is a stored route +
-- query string, replayed through the endpoints that already enforce every guardrail in SQL.
--
-- `route` and `query_string` are stored separately rather than as one URL so a view can be
-- validated against an allow-list of known routes without parsing, and so a later route rename
-- is a single UPDATE rather than a string rewrite across every saved row.

CREATE TABLE salary_schema.saved_views (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id      uuid        NOT NULL REFERENCES salary_schema.users(id) ON DELETE CASCADE,
  name          text        NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 80),
  route         text        NOT NULL CHECK (length(route) BETWEEN 1 AND 200),
  query_string  text        NOT NULL DEFAULT '' CHECK (length(query_string) <= 2000),
  shared        boolean     NOT NULL DEFAULT false,
  created_at    timestamptz NOT NULL DEFAULT now(),

  -- One name per owner. Re-saving under an existing name is an update, not a silent duplicate
  -- the owner then cannot tell apart in a picker.
  CONSTRAINT saved_views_name_unique_per_owner UNIQUE (owner_id, name)
);

-- The picker's only two reads: "my views" and "views shared with me".
CREATE INDEX saved_views_owner_idx ON salary_schema.saved_views (owner_id, name);
CREATE INDEX saved_views_shared_idx ON salary_schema.saved_views (shared) WHERE shared;
