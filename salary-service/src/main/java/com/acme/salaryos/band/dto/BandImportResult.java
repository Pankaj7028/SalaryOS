package com.acme.salaryos.band.dto;

import java.util.List;

/** FR-4.6: {@code dryRun} rows are diffed only, never applied — {@code rowsApplied} is always 0 when {@code dryRun} is true. */
public record BandImportResult(
		boolean dryRun, int totalRows, int created, int versioned, int errors, int rowsApplied,
		List<BandImportRowResult> rows) {
}
