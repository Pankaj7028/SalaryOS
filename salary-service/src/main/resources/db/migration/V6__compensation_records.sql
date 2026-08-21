-- V6: compensation_records — insert-only pay ledger (CLAUDE.md §6.3). A change never UPDATEs a
-- row; it closes the open period (effective_to) and inserts a new one. comp_no_overlap is the
-- database-level backstop for that rule — the service must not be the only thing enforcing it.
-- compa_ratio and band_id are snapshots taken at write time, not derived on read.

CREATE TABLE salary_schema.compensation_records (
  id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id              uuid NOT NULL REFERENCES salary_schema.employees(id),
  effective_from           date NOT NULL,
  effective_to             date,
  validity                 daterange GENERATED ALWAYS AS
                             (daterange(effective_from, effective_to, '[)')) STORED,
  base_amount              numeric(15,2) NOT NULL CHECK (base_amount > 0),
  currency                 char(3)       NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  pay_frequency            text          NOT NULL,
  annual_base_amount       numeric(15,2) NOT NULL,
  normalized_annual_base   numeric(15,2) NOT NULL,
  base_currency            char(3)       NOT NULL,
  fx_rate_id               uuid          NOT NULL REFERENCES salary_schema.fx_rates(id),
  band_id                  uuid          REFERENCES salary_schema.salary_bands(id),
  compa_ratio              numeric(6,4),
  range_penetration        numeric(6,4),
  change_id                uuid,
  change_reason            text NOT NULL,
  superseded_by            uuid REFERENCES salary_schema.compensation_records(id),
  created_by               uuid NOT NULL REFERENCES salary_schema.users(id),
  created_at               timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT comp_dates_ordered CHECK (effective_to IS NULL OR effective_to > effective_from),
  CONSTRAINT comp_no_overlap
    EXCLUDE USING gist (employee_id WITH =, validity WITH &&)
);

CREATE INDEX ON salary_schema.compensation_records (fx_rate_id);
CREATE INDEX ON salary_schema.compensation_records (band_id);

CREATE TABLE salary_schema.compensation_components (
  id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  compensation_record_id    uuid NOT NULL REFERENCES salary_schema.compensation_records(id),
  component_type            text NOT NULL,
  amount                    numeric(15,2) NOT NULL,
  currency                  char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  percent_of_base           numeric(6,4),
  is_recurring              boolean NOT NULL DEFAULT true
);

CREATE INDEX ON salary_schema.compensation_components (compensation_record_id);
