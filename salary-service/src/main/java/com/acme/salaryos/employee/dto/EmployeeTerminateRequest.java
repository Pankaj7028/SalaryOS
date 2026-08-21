package com.acme.salaryos.employee.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EmployeeTerminateRequest(@NotNull LocalDate terminationDate) {
}
