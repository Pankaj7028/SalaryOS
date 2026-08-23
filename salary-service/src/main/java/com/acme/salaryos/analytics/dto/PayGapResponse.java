package com.acme.salaryos.analytics.dto;

import com.acme.salaryos.common.money.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * FR-6.4 / FR-6.8. {@code unadjusted*} ignores job level entirely — the org-wide gender-pay
 * comparison a headline number usually means. {@code levelAdjustedCohorts} is the job-level ×
 * country cohort table (ui doc §8.8) — the gap within each level, which controls for level by
 * construction rather than any statistical adjustment. The two are reported as separate,
 * separately-labelled figures on purpose (ui doc §8.8): conflating an org-wide mix effect with a
 * like-for-like comparison is exactly how a pay-gap number stops being defensible.
 *
 * <p>{@code suppressedCohorts} counts every level×country pairing that had some demographic
 * coverage but produced no comparable row — either because a demographic group there had fewer
 * than five people (the privacy threshold FR-6.4 names explicitly), or because only one group was
 * represented at all (nothing to compare). Both reasons mean "not shown here," which is what the
 * reader actually needs to know; the query itself never returns a group under five either way.
 */
public record PayGapResponse(
		LocalDate asAtDate,
		String baseCurrency,
		AnalyticsPopulation population,
		FxBasis fxBasis,
		List<PayGapGroupMedian> unadjustedGroups,
		Money unadjustedGapAmount,
		BigDecimal unadjustedGapPercent,
		List<PayGapCohortRow> levelAdjustedCohorts,
		int suppressedCohorts) {
}
