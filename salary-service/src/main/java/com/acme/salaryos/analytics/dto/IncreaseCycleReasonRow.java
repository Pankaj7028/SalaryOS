package com.acme.salaryos.analytics.dto;

import com.acme.salaryos.common.money.Money;

import java.math.BigDecimal;

/** FR-6.5: one {@code change_reason}'s slice of the cycle — count, total spend, and the
 * average/median increase percent within that reason alone. */
public record IncreaseCycleReasonRow(
		String reasonCode, int count, Money totalIncrease, BigDecimal avgIncreasePercent, BigDecimal medianIncreasePercent) {
}
