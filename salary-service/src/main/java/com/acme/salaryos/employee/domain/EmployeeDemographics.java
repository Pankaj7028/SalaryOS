package com.acme.salaryos.employee.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Restricted (CLAUDE.md §6.6): never rendered per person, reaches the UI only as an aggregate
 * over a cohort of five or more. Deliberately outside the {@code employee} DTO package.
 */
@Entity
@Table(name = "employee_demographics")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EmployeeDemographics {

	@Id
	private UUID employeeId;

	private String gender;

	private LocalDate dateOfBirth;

	private String ethnicityCode;

}
