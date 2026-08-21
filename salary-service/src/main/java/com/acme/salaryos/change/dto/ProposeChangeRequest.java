package com.acme.salaryos.change.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FR-5.2. {@code newBaseAmount} is annual — {@code compensation_changes} has no {@code
 * pay_frequency} column, unlike the ledger, so a proposal is always phrased as "their new annual
 * salary," matching how a merit/promotion conversation actually happens.
 */
public record ProposeChangeRequest(
		@NotNull UUID employeeId,
		@NotNull LocalDate effectiveDate,
		@NotNull @Positive BigDecimal newBaseAmount,
		@NotBlank String currency,
		@NotBlank String changeReason,
		String performanceRating,
		String note) {
}
