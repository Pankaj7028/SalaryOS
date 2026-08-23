package com.acme.salaryos.analytics.dto;

import com.acme.salaryos.common.money.Money;

import java.time.LocalDate;
import java.util.List;

/**
 * FR-6.2 / FR-6.8. {@code totalCostToMinimum} is in {@code baseCurrency} — a sum across
 * employees who may each be paid (and each have a band) in a different native currency can only
 * be reported as one number once normalised, so unlike {@link OutOfBandRow#gapAmount}, this one
 * figure alone leaves native currency and uses each row's own already-pinned FX rate (CLAUDE.md
 * §6.4 — never a live rate) implicit in {@code normalized_annual_base}.
 */
public record OutOfBandResponse(
		LocalDate asAtDate,
		String baseCurrency,
		AnalyticsPopulation population,
		FxBasis fxBasis,
		int belowMinCount,
		int aboveMaxCount,
		Money totalCostToMinimum,
		List<OutOfBandRow> rows) {
}
