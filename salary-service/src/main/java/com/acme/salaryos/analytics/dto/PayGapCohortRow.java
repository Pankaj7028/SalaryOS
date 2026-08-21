package com.acme.salaryos.analytics.dto;

import com.acme.salaryos.common.money.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * FR-6.4's "level-adjusted" view — one job-level × country cohort, each surviving demographic
 * group's median (never fewer than five people), and the spread between the highest and lowest
 * group median. With more than two groups present, {@code gapAmount}/{@code gapPercent} are the
 * highest-minus-lowest range rather than a single directional "A vs B" figure — the schema has no
 * fixed two-value gender enumeration to assume, so "the gap" is defined as the widest spread
 * actually observed, always well-defined regardless of how many groups a cohort has.
 */
public record PayGapCohortRow(
		UUID jobLevelId,
		String jobLevelLabel,
		String countryCode,
		String countryLabel,
		List<PayGapGroupMedian> groups,
		Money gapAmount,
		BigDecimal gapPercent) {
}
