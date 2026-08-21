package com.acme.salaryos.employee.dto;

import com.acme.salaryos.common.money.Money;

import java.math.BigDecimal;

public record CompensationComponentResponse(
		String componentType, Money amount, BigDecimal percentOfBase, boolean isRecurring) {
}
