package com.acme.salaryos.change.dto;

import com.acme.salaryos.common.money.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * ui doc §8.5: the Changes screen's row shape — employee identity and proposer/decider names are
 * resolved server-side (same "never a raw id on a display surface" discipline as P4.3's CSV
 * export) rather than requiring the frontend to cross-reference a separate users lookup, which
 * would need its own RBAC surface (`UserAdminController` is HR_ADMIN-only, P8.1, not built).
 * {@code outOfBand} mirrors the exact rule {@code requireNoteIfNeeded} enforces at propose time.
 * {@code deltaAmount}/{@code deltaPercent} exist so the screen never subtracts {@code newBase}
 * from {@code currentBase} in TypeScript (CLAUDE.md §6.1) — same reasoning P5.5 flagged as a gap
 * for the pay-history ledger, not repeated here.
 */
public record ChangeResponse(
		UUID id,
		UUID employeeId,
		String employeeFirstName,
		String employeeLastName,
		String employeeNumber,
		String status,
		LocalDate effectiveDate,
		Money currentBase,
		Money newBase,
		Money deltaAmount,
		BigDecimal deltaPercent,
		String changeReason,
		String performanceRating,
		String note,
		boolean outOfBand,
		UUID proposedBy,
		String proposedByName,
		Instant proposedAt,
		UUID decidedBy,
		String decidedByName,
		Instant decidedAt,
		String decisionNote,
		Instant appliedAt,
		UUID appliedRecordId) {
}
