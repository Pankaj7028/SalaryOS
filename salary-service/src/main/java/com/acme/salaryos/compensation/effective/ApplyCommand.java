package com.acme.salaryos.compensation.effective;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Opens a new pay period — the very first one for an employee, or a raise/promotion on top of an existing one. */
public record ApplyCommand(
		UUID employeeId,
		LocalDate effectiveFrom,
		BigDecimal amount,
		String currency,
		String payFrequency,
		String changeReason,
		UUID changeId,
		UUID createdBy) {
}
