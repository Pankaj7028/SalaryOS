package com.acme.salaryos.employee.dto;

import com.acme.salaryos.common.money.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Employee detail — identity, org placement, and current pay. Ledger is a separate endpoint (P5.4). */
public record EmployeeDetailResponse(
		UUID id,
		String employeeNumber,
		String firstName,
		String lastName,
		String workEmail,
		UUID departmentId,
		UUID locationId,
		UUID jobFamilyId,
		UUID jobLevelId,
		UUID managerId,
		String employmentType,
		BigDecimal fte,
		String status,
		LocalDate hireDate,
		LocalDate terminationDate,
		boolean bandMismatched,
		Money currentBasePay,
		BigDecimal compaRatio,
		BigDecimal rangePenetration,
		String bandStatus,
		BandBoundaries band,
		List<CompensationComponentResponse> components) {
}
