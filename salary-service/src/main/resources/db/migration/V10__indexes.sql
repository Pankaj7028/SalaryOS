-- V10: indexes from Technical-Requirements.md §4.3.
-- `employees (job_level_id)` is already covered by the single-column index V3 created for the FK
-- itself — not repeated here.

CREATE INDEX idx_employees_department_status ON salary_schema.employees (department_id, status);
CREATE INDEX idx_employees_location_status ON salary_schema.employees (location_id, status);
CREATE INDEX idx_employees_last_name_id ON salary_schema.employees (last_name, id);
CREATE INDEX idx_employees_name_trgm ON salary_schema.employees
  USING gin (lower(first_name || ' ' || last_name) gin_trgm_ops);

CREATE INDEX idx_comp_records_employee_effective_from
  ON salary_schema.compensation_records (employee_id, effective_from DESC);

CREATE INDEX idx_employee_current_comp_band_status
  ON salary_schema.employee_current_comp (band_status);

CREATE INDEX idx_audit_events_occurred_at ON salary_schema.audit_events (occurred_at DESC);
CREATE INDEX idx_audit_events_entity ON salary_schema.audit_events (entity_type, entity_id);
