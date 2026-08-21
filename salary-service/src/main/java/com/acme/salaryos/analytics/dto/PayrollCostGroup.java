package com.acme.salaryos.analytics.dto;

import com.acme.salaryos.common.money.Money;

/** One row of a payroll-cost breakdown (by country, department, or level) — FR-6.1: "headcount
 * and average alongside every total." */
public record PayrollCostGroup(String key, String label, int headcount, Money totalAnnualBase, Money averageAnnualBase) {
}
