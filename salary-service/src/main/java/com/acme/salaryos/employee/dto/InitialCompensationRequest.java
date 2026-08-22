package com.acme.salaryos.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** A new hire's first-ever pay period, effective on their hire date, {@code change_reason =
 * INITIAL} (reserved for exactly this since V11's reason-code vocabulary). Always an annual
 * figure — same convention {@code ProposeChangeRequest} already uses, so this never reintroduces
 * pay-frequency handling into a flow the rest of the change lifecycle doesn't expose either. */
public record InitialCompensationRequest(@NotNull @Positive BigDecimal amount, @NotBlank String currency) {
}
