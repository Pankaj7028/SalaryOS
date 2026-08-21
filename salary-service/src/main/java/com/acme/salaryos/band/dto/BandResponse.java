package com.acme.salaryos.band.dto;

import com.acme.salaryos.common.money.Money;

import java.time.LocalDate;
import java.util.UUID;

public record BandResponse(
		UUID id,
		UUID jobLevelId,
		String countryCode,
		Money min,
		Money mid,
		Money max,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		String note,
		/** Employees currently projected against this exact band version (ui doc §8.6's grid-cell count). Always 0 for a superseded (closed) version — {@code employee_current_comp} only ever points at the in-force one. */
		long headcount) {
}
