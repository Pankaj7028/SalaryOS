package com.acme.salaryos.analytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * FR-6.1 / FR-6.8. {@code fxRateMonth} is deliberately {@code null} here: every figure summed
 * comes from {@code employee_current_comp.normalized_annual_base}, which is already pinned to
 * whichever FX rate was in force when *that employee's own record* was written (CLAUDE.md §6.4)
 * — a population spanning many employees has no single governing rate month to report, and
 * inventing one (e.g. "today's month") would misleadingly imply this report recomputed anything
 * at a live rate, which it never does. `overall` carries the same total as the sum of any one
 * breakdown's rows (by construction — same query, same filter), so the UI never has to reconcile
 * two numbers that should already agree.
 */
public record PayrollCostResponse(
		LocalDate asAtDate,
		String baseCurrency,
		AnalyticsPopulation population,
		PayrollCostGroup overall,
		List<PayrollCostGroup> byCountry,
		List<PayrollCostGroup> byDepartment,
		List<PayrollCostGroup> byLevel) {
}
