package com.acme.salaryos.analytics.dto;

import com.acme.salaryos.common.money.Money;

/**
 * FR-6.4: one demographic group's median pay within a comparison (org-wide unadjusted, or one
 * level×country cohort). Never present unless the group has at least five people — the query that
 * produces this never fetches a smaller one (CLAUDE.md §6.6, backend doc §6).
 */
public record PayGapGroupMedian(String group, int count, Money median) {
}
