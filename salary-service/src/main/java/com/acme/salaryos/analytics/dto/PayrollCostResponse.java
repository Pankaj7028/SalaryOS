package com.acme.salaryos.analytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * FR-6.1 / FR-6.8. Every figure summed here comes from
 * {@code employee_current_comp.normalized_annual_base}, already pinned to whichever FX rate was in
 * force when <em>that employee's own record</em> was written (CLAUDE.md §6.4). A population
 * spanning many employees therefore has no single governing rate month, which is why this reports
 * an {@link FxBasis} span rather than a scalar {@code fxRateMonth} — see that record for the full
 * reasoning (P10.1; it previously reported {@code null} for the same reason, which was right about
 * the problem and silent about the basis).
 *
 * <p>{@code overall} carries the same total as the sum of any one breakdown's rows (by
 * construction — same query, same filter), so the UI never has to reconcile two numbers that
 * should already agree.
 */
public record PayrollCostResponse(
		LocalDate asAtDate,
		String baseCurrency,
		AnalyticsPopulation population,
		FxBasis fxBasis,
		PayrollCostGroup overall,
		List<PayrollCostGroup> byCountry,
		List<PayrollCostGroup> byDepartment,
		List<PayrollCostGroup> byLevel) {
}
