package com.acme.salaryos.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** FR-6.3 / FR-6.8. {@code p25}/{@code median}/{@code p75} and {@code histogram} describe the
 * population under whatever {@code departmentId}/{@code jobLevelId}/{@code countryCode} filters
 * the request supplied (all optional); the three {@code by*} breakdowns apply those same filters
 * and then group on top, so "median compa-ratio per group" never means a second round trip. */
public record CompaRatioDistributionResponse(
		LocalDate asAtDate,
		AnalyticsPopulation population,
		BigDecimal p25,
		BigDecimal median,
		BigDecimal p75,
		List<CompaRatioHistogramBucket> histogram,
		List<CompaRatioGroupMedian> byDepartment,
		List<CompaRatioGroupMedian> byLevel,
		List<CompaRatioGroupMedian> byCountry) {
}
