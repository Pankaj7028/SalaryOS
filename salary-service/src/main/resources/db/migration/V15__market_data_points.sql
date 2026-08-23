-- V15: market benchmark data points (P11.5, F10 — the "reframed" market feature).
--
-- Salary OS does not and will not have benchmark data of its own: that is a data business (Pave,
-- Ravio and Figures each spent years building contributor networks; Barley licenses from Mercer
-- instead), and a single-tenant tool for one company has no contributor network. So this is the
-- SEAM, not the dataset — ACME loads whatever survey it already buys, and the band screens can
-- then answer "is our band competitive?" using data ACME already owns.
--
-- Effective-dated by month, matching how salary surveys are actually published, and versioned by
-- (source, job level, country, month) so re-importing a corrected survey replaces rather than
-- duplicates. Percentiles are stored in the survey's own currency — never normalised here, because
-- normalising a benchmark at import time would bake in one month's FX rate and make the figure
-- move for reasons that have nothing to do with the market.

CREATE TABLE salary_schema.market_data_points (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source          text    NOT NULL CHECK (length(btrim(source)) BETWEEN 1 AND 80),
  job_level_id    uuid    NOT NULL REFERENCES salary_schema.job_levels(id),
  country_code    char(2) NOT NULL REFERENCES salary_schema.countries(code),
  currency        char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  p25_amount      numeric(15,2) NOT NULL CHECK (p25_amount > 0),
  p50_amount      numeric(15,2) NOT NULL CHECK (p50_amount > 0),
  p75_amount      numeric(15,2) NOT NULL CHECK (p75_amount > 0),
  effective_month date    NOT NULL CHECK (extract(day from effective_month) = 1),
  imported_by     uuid    NOT NULL REFERENCES salary_schema.users(id),
  imported_at     timestamptz NOT NULL DEFAULT now(),

  -- Same shape as salary_bands' own band_ordered check: percentiles that cross are not a survey,
  -- they are a transposed column.
  CONSTRAINT market_percentiles_ordered
    CHECK (p25_amount <= p50_amount AND p50_amount <= p75_amount),

  -- One data point per source, level, country and month. Re-importing a corrected survey updates
  -- in place rather than silently leaving two contradictory p50s for the same cell.
  CONSTRAINT market_point_unique
    UNIQUE (source, job_level_id, country_code, effective_month)
);

-- The read the band screens make: "the most recent point for this level and country".
CREATE INDEX market_data_points_lookup_idx
  ON salary_schema.market_data_points (job_level_id, country_code, effective_month DESC);
