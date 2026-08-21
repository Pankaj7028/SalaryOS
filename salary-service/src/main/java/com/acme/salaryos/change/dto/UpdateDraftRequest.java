package com.acme.salaryos.change.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Edits a draft in place (the one mutation a draft permits — PATCH /changes/{id}, DRAFT only). */
public record UpdateDraftRequest(
		@NotNull LocalDate effectiveDate,
		@NotNull @Positive BigDecimal newBaseAmount,
		@NotBlank String currency,
		@NotBlank String changeReason,
		String performanceRating,
		String note) {
}
