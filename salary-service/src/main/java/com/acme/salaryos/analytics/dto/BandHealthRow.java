package com.acme.salaryos.analytics.dto;

import com.acme.salaryos.common.money.Money;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One in-force band, judged as a piece of structure rather than as a lookup table.
 *
 * <p>{@code rangeSpread} is {@code max/min - 1}. {@code midpointProgression} is this band's mid
 * against the previous level's mid in the same job family and country — {@code null} for the
 * lowest level, which has nothing beneath it to progress from. {@code gapToPreviousLevel} is true
 * when this band's minimum sits above the previous level's maximum: overlap between adjacent bands
 * is normal and healthy, a *gap* is a promotion cliff where someone can be promoted and take a pay
 * cut relative to the band they were topping out in.
 *
 * <p>{@code marketP50} is the most recently imported market median for this (job level, country),
 * and {@code midVsMarketP50} is {@code mid / p50 - 1} — how far this band's midpoint sits above or
 * below the market (P11.6/F10). Both are null when no survey has been imported for the pair, which
 * is the ordinary case: Salary OS ships the seam for market data, not a dataset. Both are also null
 * when the survey is denominated differently from the band — a GBP band judged against a USD median
 * would read as ~25% under market for no reason anyone could act on, and converting would pin the
 * benchmark to one month's FX rate and make it drift for reasons unrelated to the market.
 *
 * <p>Every figure is in the band's own currency, so nothing here is normalised or FX-dependent.
 */
public record BandHealthRow(
		UUID bandId,
		String jobFamily,
		String levelCode,
		String levelTitle,
		String countryCode,
		String countryName,
		Money min,
		Money mid,
		Money max,
		BigDecimal rangeSpread,
		BigDecimal midpointProgression,
		boolean gapToPreviousLevel,
		int incumbents,
		BigDecimal medianCompaRatio,
		int monthsSinceVersioned,
		Money marketP50,
		BigDecimal midVsMarketP50) {
}
