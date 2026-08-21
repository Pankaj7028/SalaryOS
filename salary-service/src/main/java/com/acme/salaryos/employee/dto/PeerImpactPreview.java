package com.acme.salaryos.employee.dto;

import com.acme.salaryos.common.money.Money;

/**
 * P6.4: {@link PeerComparisonResponse} plus a hypothetical "after" rank — the propose-change
 * dialog's "peer percentile before and after." {@code p25}/{@code median}/{@code p75} describe the
 * cohort as it stands today in both cases; only {@code percentileAfter} reflects the hypothetical.
 */
public record PeerImpactPreview(
		int cohortSize, boolean suppressed, Money p25, Money median, Money p75,
		Integer percentileBefore, Integer percentileAfter) {
}
