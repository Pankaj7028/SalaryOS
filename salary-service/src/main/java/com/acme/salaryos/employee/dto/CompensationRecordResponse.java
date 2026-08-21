package com.acme.salaryos.employee.dto;

import com.acme.salaryos.common.money.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One ledger entry (FR-3.6/FR-6.7). {@code note}, proposer, and approver aren't here — they live
 * on the {@code compensation_changes} row this record's {@code changeId} points at, and that
 * domain doesn't exist yet (P6.1). {@code changeId}/{@code supersededBy} are included now so the
 * P5.5/P6 UI can wire them up without another backend round-trip.
 */
public record CompensationRecordResponse(
		UUID id,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		Money base,
		String payFrequency,
		BigDecimal annualBaseAmount,
		Money normalizedAnnualBase,
		UUID bandId,
		BigDecimal compaRatio,
		BigDecimal rangePenetration,
		String changeReason,
		UUID changeId,
		UUID supersededBy,
		UUID createdBy,
		Instant createdAt) {
}
