package com.acme.salaryos.employee.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeCreateRequest(
		@NotBlank String employeeNumber,
		@NotBlank String firstName,
		@NotBlank String lastName,
		@NotBlank @Email String workEmail,
		@NotNull UUID departmentId,
		@NotNull UUID locationId,
		@NotNull UUID jobFamilyId,
		@NotNull UUID jobLevelId,
		UUID managerId,
		@NotNull LocalDate hireDate,
		@NotBlank String employmentType,
		@NotNull @DecimalMin("0.01") @DecimalMax("1.00") BigDecimal fte) {
}
