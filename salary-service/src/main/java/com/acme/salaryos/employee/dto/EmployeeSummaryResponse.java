package com.acme.salaryos.employee.dto;

import com.acme.salaryos.common.money.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One employee-list row (FR-2.3): identity, org placement, and current pay — {@code currentBasePay}
 * / {@code compaRatio} / {@code bandStatus} are null until a compensation record exists for this
 * employee (P5), never a fabricated default (CLAUDE.md §6: a salary is never shown without its
 * band, and a missing band is null, not a compa-ratio of 1.0).
 */
public record EmployeeSummaryResponse(
		UUID id,
		String employeeNumber,
		String firstName,
		String lastName,
		String workEmail,
		UUID departmentId,
		UUID locationId,
		UUID jobLevelId,
		String employmentType,
		BigDecimal fte,
		String status,
		LocalDate hireDate,
		LocalDate terminationDate,
		boolean bandMismatched,
		Money currentBasePay,
		BigDecimal compaRatio,
		String bandStatus) {
}
