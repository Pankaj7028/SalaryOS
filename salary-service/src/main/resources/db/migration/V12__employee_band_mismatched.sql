-- V12 (fix-forward, P4.2): FR-2.5 — editing job level or location does not change pay; it flags
-- the employee as band-mismatched until a compensation change resolves it. This is tracked as a
-- plain boolean set on edit, not derived by comparing bands live: the rule is "level/location
-- changed since pay last did", not "the numbers technically differ today". Cleared when P5's
-- EffectiveDating applies a new compensation record for the employee.

ALTER TABLE salary_schema.employees
  ADD COLUMN band_mismatched boolean NOT NULL DEFAULT false;
