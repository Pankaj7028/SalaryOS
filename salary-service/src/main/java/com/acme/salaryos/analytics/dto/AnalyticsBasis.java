package com.acme.salaryos.analytics.dto;

/**
 * What a payroll-cost figure counts. FR-3.4 stores recurring components alongside base and says
 * each "is included in total target cash" — before P10.6 no analytic read them, so the product
 * could not answer what ACME actually spends, only what it pays in base.
 *
 * <p><strong>Only payroll cost takes a basis.</strong> Compa-ratio deliberately does not: a salary
 * band is a <em>base pay</em> range ({@code compaRatio(annualBaseAmount, band)} in
 * {@code EffectiveDating}), so dividing total target cash by a base-pay midpoint would push
 * everyone with a bonus target above 1.0 and make the metric mean nothing. Same for range
 * penetration and every out-of-band judgement — they compare base to a base-pay band, and that is
 * correct.
 */
public enum AnalyticsBasis {

	/** Annualised base pay alone — what every figure meant before P10.6, and still the default. */
	BASE,

	/** Base plus recurring components, each normalised at the rate its own record pinned. */
	TOTAL_TARGET_CASH
}
