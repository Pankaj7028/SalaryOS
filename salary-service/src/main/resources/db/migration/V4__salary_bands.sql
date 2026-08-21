-- V4: salary_bands, effective-dated per (job level × country), never mutated in place (FR-4.5).

CREATE TABLE salary_schema.salary_bands (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_level_id    uuid    NOT NULL REFERENCES salary_schema.job_levels(id),
  country_code    char(2) NOT NULL REFERENCES salary_schema.countries(code),
  currency        char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  min_amount      numeric(15,2) NOT NULL CHECK (min_amount > 0),
  mid_amount      numeric(15,2) NOT NULL CHECK (mid_amount > 0),
  max_amount      numeric(15,2) NOT NULL CHECK (max_amount > 0),
  effective_from  date NOT NULL,
  effective_to    date,
  created_by      uuid NOT NULL REFERENCES salary_schema.users(id),
  note            text,
  CONSTRAINT band_ordered CHECK (min_amount <= mid_amount AND mid_amount <= max_amount),
  CONSTRAINT band_dates_ordered CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX ON salary_schema.salary_bands (job_level_id, country_code);
