package com.acme.salaryos.analytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * FR-6.1 / FR-6.8. {@code population.headcount} excludes terminated employees by default
 * (FR-2.6), same as {@link PayrollCostResponse}; {@code byStatus} still reports every status,
 * including {@code TERMINATED}, so the excluded count is never simply invisible.
 */
public record HeadcountResponse(
		LocalDate asAtDate,
		AnalyticsPopulation population,
		List<HeadcountGroup> byCountry,
		List<HeadcountGroup> byDepartment,
		List<HeadcountGroup> byLevel,
		List<HeadcountGroup> byStatus) {
}
