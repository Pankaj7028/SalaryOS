-- V9: employee_current_comp — the current-pay projection (Technical-Requirements.md §4.4). A
-- cache maintained transactionally by the service, not a trigger; the ledger is the truth, and
-- ProjectionConsistencyTest (P5.2) re-derives it and asserts equality.

CREATE TABLE salary_schema.employee_current_comp (
  employee_id              uuid PRIMARY KEY REFERENCES salary_schema.employees(id),
  compensation_record_id   uuid NOT NULL REFERENCES salary_schema.compensation_records(id),
  base_amount              numeric(15,2) NOT NULL,
  currency                 char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  annual_base_amount       numeric(15,2) NOT NULL,
  normalized_annual_base   numeric(15,2) NOT NULL,
  band_id                  uuid REFERENCES salary_schema.salary_bands(id),
  compa_ratio              numeric(6,4),
  range_penetration        numeric(6,4),
  band_status              text NOT NULL
                             CHECK (band_status IN ('IN_BAND', 'BELOW_MIN', 'ABOVE_MAX', 'NO_BAND')),
  refreshed_at             timestamptz NOT NULL DEFAULT now()
);

-- No explicit GRANT needed: V8's ALTER DEFAULT PRIVILEGES already covers every table this same
-- connecting role creates from here on, standard CRUD to salaryos_app.
