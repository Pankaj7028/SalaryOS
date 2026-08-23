package com.acme.salaryos.market.dto;

import java.util.List;

/** {@code dryRun} rows are diffed only, never applied — {@code rowsApplied} is 0 when it is true. */
public record MarketImportResult(
		boolean dryRun, int totalRows, int created, int updated, int errors, int rowsApplied,
		List<MarketImportRowResult> rows) {
}
