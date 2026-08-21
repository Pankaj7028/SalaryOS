package com.acme.salaryos.analytics.dto;

import com.acme.salaryos.common.money.Money;

import java.util.UUID;

/**
 * FR-6.2: one employee paid below their band's minimum or above its maximum. {@code gapAmount}
 * is always positive, in the band's own currency (the same currency the employee is paid in at
 * that location) — how far below min, or how far above max, never a signed delta the reader has
 * to interpret.
 */
public record OutOfBandRow(
		UUID employeeId,
		String employeeFirstName,
		String employeeLastName,
		String employeeNumber,
		UUID departmentId,
		UUID locationId,
		UUID jobLevelId,
		String bandStatus,
		Money currentBase,
		Money bandMin,
		Money bandMid,
		Money bandMax,
		Money gapAmount) {
}
