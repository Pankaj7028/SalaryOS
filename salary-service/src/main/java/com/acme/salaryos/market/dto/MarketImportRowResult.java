package com.acme.salaryos.market.dto;

/**
 * {@code action} is {@code CREATE} (first point for this source × level × country × month),
 * {@code UPDATE} (a corrected survey replacing one already loaded), or {@code ERROR} (row
 * rejected — the rest of the file still imports, matching the bands importer's own rule).
 */
public record MarketImportRowResult(
		int rowNumber,
		String action,
		String source,
		String countryCode,
		String error) {
}
