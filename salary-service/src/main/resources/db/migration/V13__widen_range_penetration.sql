-- V13: widen range_penetration from numeric(6,4) (max abs value under 100) to numeric(8,4).
-- Range penetration legitimately exceeds 100 for anyone paid above band max — P5.1's
-- EffectiveDating.apply() is the first real write path to compute this value from a live
-- transaction, and would overflow on the very first above-max raise. Fix-forward per CLAUDE.md
-- §12.11; V6/V9 stay as committed.

ALTER TABLE salary_schema.compensation_records
  ALTER COLUMN range_penetration TYPE numeric(8,4);

ALTER TABLE salary_schema.employee_current_comp
  ALTER COLUMN range_penetration TYPE numeric(8,4);
