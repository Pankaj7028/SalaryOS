package com.acme.salaryos.analytics.dto;

import java.util.Map;

/**
 * FR-6.8: every analytics response states its population, including what was excluded and why.
 * {@code excluded} is a reason → count map (currently only {@code "terminated"} — FR-2.6's
 * default exclusion — can appear) so the UI can say what it is not showing rather than leave a
 * silent gap between "total employees" and "employees counted here."
 */
public record AnalyticsPopulation(int headcount, Map<String, Integer> excluded) {
}
