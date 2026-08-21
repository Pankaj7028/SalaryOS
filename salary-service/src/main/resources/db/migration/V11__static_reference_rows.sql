-- V11: seed-independent reference rows — currencies (`GET /reference/currencies`) and the
-- compensation reason-code vocabulary (FR-5.2, plus INITIAL for a first-ever hire record that
-- isn't the result of a change proposal). Unlike P9's seeded employees/comp records, these do not
-- depend on APP_SEED_RANDOM_SEED — every environment gets the same rows.

CREATE TABLE salary_schema.currencies (
  code   char(3) PRIMARY KEY CHECK (code ~ '^[A-Z]{3}$'),
  name   text NOT NULL
);

INSERT INTO salary_schema.currencies (code, name) VALUES
  ('USD', 'US Dollar'),
  ('EUR', 'Euro'),
  ('GBP', 'British Pound'),
  ('INR', 'Indian Rupee'),
  ('CAD', 'Canadian Dollar'),
  ('AUD', 'Australian Dollar'),
  ('JPY', 'Japanese Yen'),
  ('SGD', 'Singapore Dollar'),
  ('CHF', 'Swiss Franc'),
  ('BRL', 'Brazilian Real');

CREATE TABLE salary_schema.reason_codes (
  code    text PRIMARY KEY,
  label   text NOT NULL
);

INSERT INTO salary_schema.reason_codes (code, label) VALUES
  ('INITIAL', 'Initial hire'),
  ('MERIT', 'Merit increase'),
  ('PROMOTION', 'Promotion'),
  ('MARKET_ADJUSTMENT', 'Market adjustment'),
  ('ROLE_CHANGE', 'Role change'),
  ('LOCATION_CHANGE', 'Location change'),
  ('CORRECTION', 'Correction'),
  ('DEMOTION', 'Demotion');
