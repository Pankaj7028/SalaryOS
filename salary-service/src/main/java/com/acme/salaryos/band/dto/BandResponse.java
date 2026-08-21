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
		String note) {
}
