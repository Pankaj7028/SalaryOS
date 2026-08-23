package com.acme.salaryos.analytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * P11.1. The seed generates *deliberate* anomalies so every screen has something to show
 * (FR-8.4); a real import from ACME's spreadsheets will generate accidental ones, and nothing
 * surfaced them. Replacing spreadsheets is the stated goal, and the day-one job of that migration
 * is finding what the spreadsheets got wrong.
 *
 * <p>No {@link FxBasis} here: this response counts rows, it does not report money.
 */
public record DataHealthResponse(
		LocalDate asAtDate,
		int totalEmployees,
		int failingChecks,
		List<DataHealthCheck> checks) {
}
