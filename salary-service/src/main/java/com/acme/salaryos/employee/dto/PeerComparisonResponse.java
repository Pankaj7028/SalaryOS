package com.acme.salaryos.employee.dto;

import com.acme.salaryos.common.money.Money;

/**
 * FR-6.6: one employee's position against the pay distribution of their cohort (same job level ×
 * country). {@code suppressed} is true, and every figure null, when the cohort has fewer than 5
 * members — matching the FR-6.4 suppression threshold used everywhere else a small group could
 * otherwise identify individuals.
 */
public record PeerComparisonResponse(
		int cohortSize, boolean suppressed, Money p25, Money median, Money p75, Integer percentile) {
}
