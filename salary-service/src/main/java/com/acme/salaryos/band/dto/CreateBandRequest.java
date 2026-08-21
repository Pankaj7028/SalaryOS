package com.acme.salaryos.band.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateBandRequest(
		@NotNull UUID jobLevelId,
		@NotBlank String countryCode,
		@NotBlank String currency,
		@NotNull @Positive BigDecimal minAmount,
		@NotNull @Positive BigDecimal midAmount,
		@NotNull @Positive BigDecimal maxAmount,
		@NotNull LocalDate effectiveFrom,
		String note) {
}
