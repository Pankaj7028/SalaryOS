-- V2: organisation reference data (countries, locations, departments, job families, job levels)

CREATE TABLE salary_schema.countries (
  code               char(2) PRIMARY KEY,
  name               text NOT NULL,
  default_currency   char(3) NOT NULL CHECK (default_currency ~ '^[A-Z]{3}$')
);

CREATE TABLE salary_schema.locations (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  country_code  char(2) NOT NULL REFERENCES salary_schema.countries(code),
  city          text    NOT NULL,
  name          text    NOT NULL,
  is_active     boolean NOT NULL DEFAULT true
);

CREATE INDEX ON salary_schema.locations (country_code);

CREATE TABLE salary_schema.departments (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name          text NOT NULL,
  code          text NOT NULL UNIQUE,
  parent_id     uuid REFERENCES salary_schema.departments(id),
  cost_centre   text
);

CREATE INDEX ON salary_schema.departments (parent_id);

CREATE TABLE salary_schema.job_families (
  id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name   text NOT NULL,
  code   text NOT NULL UNIQUE
);

CREATE TABLE salary_schema.job_levels (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_family_id  uuid NOT NULL REFERENCES salary_schema.job_families(id),
  level_code     text NOT NULL CHECK (level_code IN ('L1', 'L2', 'L3', 'L4', 'L5', 'L6', 'L7')),
  title          text NOT NULL,
  sort_order     int  NOT NULL,
  UNIQUE (job_family_id, level_code)
);

CREATE INDEX ON salary_schema.job_levels (job_family_id);
