package com.acme.salaryos.employee.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A full replace of the editable profile fields, not a partial patch — every field is required.
 * FR-2.5: changing {@code jobLevelId} or {@code locationId} here never touches pay; it flags
 * {@code bandMismatched}.
 */
public record EmployeeUpdateRequest(
		@NotBlank String firstName,
		@NotBlank String lastName,
		@NotBlank @Email String workEmail,
		@NotNull UUID departmentId,
		@NotNull UUID locationId,
		@NotNull UUID jobFamilyId,
		@NotNull UUID jobLevelId,
		UUID managerId,
		@NotBlank String employmentType,
		@NotNull @DecimalMin("0.01") @DecimalMax("1.00") BigDecimal fte) {
}
