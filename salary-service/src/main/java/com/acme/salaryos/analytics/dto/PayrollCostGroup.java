package com.acme.salaryos.analytics.dto;

import com.acme.salaryos.common.money.Money;

/**
 * One row of a payroll-cost breakdown (by country, department, or level) — FR-6.1: "headcount and
 * average alongside every total."
 *
 * <p>{@code total} and {@code average} are deliberately basis-neutral names. They were
 * {@code totalAnnualBase}/{@code averageAnnualBase} until P10.7, which was a lie on the
 * {@code TOTAL_TARGET_CASH} basis P10.6 added: the field held base plus recurring components while
 * still calling itself base. The response's own {@link AnalyticsBasis} says what is being totalled
 * (FR-6.8 — a figure has to state what it counts), so the field does not have to guess.
 */
public record PayrollCostGroup(String key, String label, int headcount, Money total, Money average) {
}
