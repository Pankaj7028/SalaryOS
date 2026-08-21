package com.acme.salaryos.band.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** {@code action} is {@code CREATE} (first band for this level × country), {@code VERSION} (supersedes an in-force one), or {@code ERROR} (row rejected, nothing applied even outside a dry run). */
public record BandImportRowResult(
		int rowNumber,
		String action,
		UUID jobLevelId,
		String countryCode,
		String currency,
		BigDecimal minAmount,
		BigDecimal midAmount,
		BigDecimal maxAmount,
		LocalDate effectiveFrom,
		String error) {
}
