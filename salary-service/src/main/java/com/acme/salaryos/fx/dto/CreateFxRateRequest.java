package com.acme.salaryos.fx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code rateMonth} is normalised to the first of its month by the service — the admin picks a month, not a day. */
public record CreateFxRateRequest(
		@NotNull LocalDate rateMonth, @NotBlank String baseCurrency, @NotBlank String quoteCurrency, @NotNull @Positive BigDecimal rate) {
}
