package com.acme.salaryos.compensation.effective;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Fixes a data-entry mistake inside an existing period — the original row is closed and superseded, never edited or deleted. */
public record CorrectCommand(
		UUID originalRecordId,
		LocalDate effectiveFrom,
		BigDecimal amount,
		String currency,
		String payFrequency,
		String note,
		UUID createdBy) {
}
