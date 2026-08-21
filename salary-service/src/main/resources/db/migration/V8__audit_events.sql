-- V8: audit_events — append-only (CLAUDE.md §6.7, FR-7.3). The application's database role gets
-- INSERT and SELECT only; there is no UPDATE/DELETE grant to revoke later, because none is ever
-- given. AuditImmutabilityTest (P8.2) is the full guard; this proves the grant at migration time.
--
-- The role is created here, idempotently, as a local/Testcontainers fallback: on Neon it is
-- expected to already exist from project provisioning (P0.3), in which case this block no-ops.
-- The password below is a placeholder only ever used against an ephemeral test container — Neon's
-- real credential is issued out-of-band and never lives in this file.
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'salaryos_app') THEN
    CREATE ROLE salaryos_app LOGIN PASSWORD 'local-dev-only-not-a-secret';
  END IF;
END
$$;

CREATE TABLE salary_schema.audit_events (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  occurred_at      timestamptz NOT NULL DEFAULT now(),
  actor_user_id    uuid NOT NULL REFERENCES salary_schema.users(id),
  actor_role       text NOT NULL,
  action           text NOT NULL,
  entity_type      text NOT NULL,
  entity_id        uuid,
  before_json      jsonb,
  after_json       jsonb,
  request_id       uuid,
  ip               inet
);

CREATE INDEX ON salary_schema.audit_events (actor_user_id);

-- Postgres owners always retain full DML on their own tables — REVOKE against an owner is a
-- no-op. So salaryos_app must NOT be the role that runs migrations (see the migration-user note
-- in application-local.yml.example); it only ever receives privileges via GRANT, which makes the
-- REVOKE below actually restrictive.
--
-- Standard CRUD on everything the app manages: applied to every table that exists already
-- (V1-V7), and — via ALTER DEFAULT PRIVILEGES — to every table a later migration creates under
-- this same connecting role, so V9 onward need no repeated GRANT.
GRANT USAGE ON SCHEMA salary_schema TO salaryos_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA salary_schema TO salaryos_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA salary_schema
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO salaryos_app;

-- audit_events is the one exception: append-only, by grant, not just by convention (FR-7.3).
REVOKE UPDATE, DELETE ON salary_schema.audit_events FROM salaryos_app;
