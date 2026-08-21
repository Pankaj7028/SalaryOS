-- V1: salary_schema, required extensions, and identity & access tables
-- (users, user_sessions, password_reset_tokens). CLAUDE.md §4 is the auth-model source of truth.

CREATE SCHEMA IF NOT EXISTS salary_schema;

-- daterange EXCLUDE USING gist on compensation_records (V6)
CREATE EXTENSION IF NOT EXISTS btree_gist;
-- name search on employees (V3)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- case-insensitive unique email
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE salary_schema.users (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email               citext      NOT NULL UNIQUE,
  full_name           text        NOT NULL,
  password_hash       text        NOT NULL,
  role                text        NOT NULL
                        CHECK (role IN ('HR_ADMIN', 'HR_MANAGER', 'COMP_ANALYST', 'AUDITOR')),
  status              text        NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE', 'INACTIVE')),
  failed_login_count  int         NOT NULL DEFAULT 0,
  locked_until        timestamptz,
  theme_preference    text        NOT NULL DEFAULT 'SYSTEM'
                        CHECK (theme_preference IN ('SYSTEM', 'LIGHT', 'DARK')),
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now()
);

-- One row per issued refresh token; jti is the access token's claim, checked on every request
-- for revocation (CLAUDE.md §4.1). family_id links the rotation chain for reuse detection (§4.4).
CREATE TABLE salary_schema.user_sessions (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id              uuid        NOT NULL REFERENCES salary_schema.users(id),
  jti                  uuid        NOT NULL UNIQUE,
  refresh_token_hash   text        NOT NULL,
  family_id            uuid        NOT NULL,
  issued_at            timestamptz NOT NULL DEFAULT now(),
  expires_at           timestamptz NOT NULL,
  revoked_at           timestamptz,
  user_agent           text,
  ip                   inet
);

CREATE INDEX ON salary_schema.user_sessions (user_id);
CREATE INDEX ON salary_schema.user_sessions (family_id);

-- Admin-issued, single-use, 30-minute reset tokens (FR-1.6). No email transport in v1 —
-- the token is displayed once to the HR Admin who generated it.
CREATE TABLE salary_schema.password_reset_tokens (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      uuid        NOT NULL REFERENCES salary_schema.users(id),
  token_hash   text        NOT NULL,
  expires_at   timestamptz NOT NULL,
  used_at      timestamptz
);

CREATE INDEX ON salary_schema.password_reset_tokens (user_id);
