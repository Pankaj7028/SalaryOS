package com.acme.salaryos.employee.dto;

/** {@code action} is {@code CREATE}, {@code UPDATE}, or {@code ERROR} — same three-way shape as
 * {@code BandImportRowResult} (P5.3), keyed here by {@code employeeNumber} instead of (level, country). */
public record EmployeeImportRowResult(
		int rowNumber, String action, String employeeNumber, String firstName, String lastName, String error) {
}
