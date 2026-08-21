package com.acme.salaryos.analytics.dto;

import java.math.BigDecimal;

/** FR-6.3: "median compa-ratio per group" — one row of the by-department/by-level/by-country
 * breakdown, under whatever filters the request already applied. */
public record CompaRatioGroupMedian(String key, String label, int count, BigDecimal medianCompaRatio) {
}
