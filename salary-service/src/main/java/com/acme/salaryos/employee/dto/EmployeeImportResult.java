package com.acme.salaryos.employee.dto;

import java.util.List;

/** P8.4's own Verify clause: {@code dryRun} rows are diffed only, never applied — {@code rowsApplied}
 * is always 0 when {@code dryRun} is true (same contract as {@code BandImportResult}, P5.3). */
public record EmployeeImportResult(
		boolean dryRun, int totalRows, int created, int updated, int errors, int rowsApplied,
		List<EmployeeImportRowResult> rows) {
}
