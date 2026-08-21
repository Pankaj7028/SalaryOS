package com.acme.salaryos.analytics.dto;

import com.acme.salaryos.common.money.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * FR-6.5 / FR-6.8: total increase spend for {@code [fromDate, toDate]}, by reason code. Only
 * {@code APPLIED} changes count — an {@code APPROVED} change with a future effective date is a
 * promise, not spend yet (CLAUDE.md §8). {@code totalIncrease} is in {@code baseCurrency},
 * normalised the same way {@link OutOfBandResponse#totalCostToMinimum} is — each applied change's
 * own linked ledger row already carries a pinned FX rate, never a live one.
 * {@code budgetBurnPercent} is {@code null} when no budget was supplied.
 */
public record IncreaseCycleResponse(
		LocalDate asAtDate,
		LocalDate fromDate,
		LocalDate toDate,
		String baseCurrency,
		AnalyticsPopulation population,
		Money totalIncrease,
		BigDecimal avgIncreasePercent,
		BigDecimal medianIncreasePercent,
		List<IncreaseCycleReasonRow> byReason,
		Money budget,
		BigDecimal budgetBurnPercent) {
}
