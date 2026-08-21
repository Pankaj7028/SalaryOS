-- V5: fx_rates, one pinned rate per (month, base currency, quote currency).
-- Normalisation never recomputes at today's rate (CLAUDE.md §6.4) — every comp record stores the
-- fx_rate_id it used, so a report run twice returns the same figure.

CREATE TABLE salary_schema.fx_rates (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  rate_month       date NOT NULL CHECK (extract(day from rate_month) = 1),
  base_currency    char(3) NOT NULL CHECK (base_currency ~ '^[A-Z]{3}$'),
  quote_currency   char(3) NOT NULL CHECK (quote_currency ~ '^[A-Z]{3}$'),
  rate             numeric(18,8) NOT NULL CHECK (rate > 0),
  UNIQUE (rate_month, base_currency, quote_currency)
);
