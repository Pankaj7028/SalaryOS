-- V3: employees and employee_demographics.
-- employee_demographics is deliberately isolated: the FK points FROM demographics TO employees,
-- never the other way, so no JPA fetch from Employee can drag a demographic field along
-- (CLAUDE.md §6.6). It is addressed only by employee_id, one row per employee, never joined
-- into an individual-employee response.

CREATE TABLE salary_schema.employees (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_number    text    NOT NULL UNIQUE,
  first_name         text    NOT NULL,
  last_name          text    NOT NULL,
  work_email         citext  NOT NULL UNIQUE,
  department_id      uuid    NOT NULL REFERENCES salary_schema.departments(id),
  location_id        uuid    NOT NULL REFERENCES salary_schema.locations(id),
  job_family_id      uuid    NOT NULL REFERENCES salary_schema.job_families(id),
  job_level_id       uuid    NOT NULL REFERENCES salary_schema.job_levels(id),
  manager_id         uuid    REFERENCES salary_schema.employees(id),
  hire_date          date    NOT NULL,
  employment_type    text    NOT NULL CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT')),
  fte                numeric(3,2) NOT NULL CHECK (fte >= 0.01 AND fte <= 1.00),
  status             text    NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'TERMINATED')),
  termination_date   date,
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT emp_termination_date_requires_status
    CHECK (termination_date IS NULL OR status = 'TERMINATED')
);

CREATE INDEX ON salary_schema.employees (department_id);
CREATE INDEX ON salary_schema.employees (location_id);
CREATE INDEX ON salary_schema.employees (job_family_id);
CREATE INDEX ON salary_schema.employees (job_level_id);
CREATE INDEX ON salary_schema.employees (manager_id);

CREATE TABLE salary_schema.employee_demographics (
  employee_id      uuid PRIMARY KEY REFERENCES salary_schema.employees(id),
  gender           text,
  date_of_birth    date,
  ethnicity_code   text
);
