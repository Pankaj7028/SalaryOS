-- V7: compensation_changes — the proposal/approval lifecycle (CLAUDE.md §8). Only APPLIED writes
-- a compensation_records row; APPROVED is a promise, not a fact, until the effective date arrives.

CREATE TABLE salary_schema.compensation_changes (
  id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id            uuid NOT NULL REFERENCES salary_schema.employees(id),
  status                 text NOT NULL
                           CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'APPLIED', 'REJECTED')),
  effective_date         date NOT NULL,
  current_base_amount    numeric(15,2) NOT NULL,
  new_base_amount        numeric(15,2) NOT NULL CHECK (new_base_amount > 0),
  currency               char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
  change_reason          text NOT NULL,
  performance_rating     text,
  note                   text,
  proposed_by            uuid NOT NULL REFERENCES salary_schema.users(id),
  proposed_at            timestamptz NOT NULL DEFAULT now(),
  decided_by             uuid REFERENCES salary_schema.users(id),
  decided_at             timestamptz,
  decision_note          text,
  applied_at             timestamptz,
  applied_record_id      uuid REFERENCES salary_schema.compensation_records(id)
);

-- An employee may have at most one non-terminal change at a time (CLAUDE.md §8); a second
-- proposal is a 409 at the service layer, backed by this index at the database.
CREATE UNIQUE INDEX one_open_change_per_employee
  ON salary_schema.compensation_changes (employee_id)
  WHERE status IN ('DRAFT', 'PENDING', 'APPROVED');

CREATE INDEX ON salary_schema.compensation_changes (proposed_by);
CREATE INDEX ON salary_schema.compensation_changes (decided_by);
