package com.acme.salaryos.change.dto;

import com.acme.salaryos.common.money.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ChangeResponse(
		UUID id,
		UUID employeeId,
		String status,
		LocalDate effectiveDate,
		Money currentBase,
		Money newBase,
		String changeReason,
		String performanceRating,
		String note,
		UUID proposedBy,
		Instant proposedAt,
		UUID decidedBy,
		Instant decidedAt,
		String decisionNote,
		Instant appliedAt,
		UUID appliedRecordId) {
}
