package com.acme.salaryos.band.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Versions the target band (FR-4.5): closes it and opens a successor carrying these new figures. */
public record UpdateBandRequest(
		@NotBlank String currency,
		@NotNull @Positive BigDecimal minAmount,
		@NotNull @Positive BigDecimal midAmount,
		@NotNull @Positive BigDecimal maxAmount,
		@NotNull LocalDate effectiveFrom,
		String note) {
}
