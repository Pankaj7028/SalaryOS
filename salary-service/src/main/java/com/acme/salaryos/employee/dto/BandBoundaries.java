package com.acme.salaryos.employee.dto;

import com.acme.salaryos.common.money.Money;

/**
 * A salary band's min/mid/max, for {@code <BandBar>} — never shown without the salary it frames.
 *
 * <p>{@code marketP50} is the most recent imported market median for this band's (job level,
 * country), or null when none has been imported (P11.6/F10). It is null far more often than not,
 * and that is the normal case rather than a degraded one: Salary OS ships the *seam* for market
 * data, not a dataset, so most installations will have bands with no benchmark against them.
 *
 * <p><b>It is only ever populated when the market point's currency matches the band's.</b> A tick
 * drawn on a GBP band scale from a USD survey figure would be a silent 25% lie on a scale the
 * reader trusts to be one currency — and CLAUDE.md §6.2 exists to stop exactly that. Converting is
 * not the fix either: it would pin a benchmark to one month's FX rate and make it drift for reasons
 * that have nothing to do with the market (P11.5's reasoning, unchanged here).
 */
public record BandBoundaries(Money min, Money mid, Money max, Money marketP50) {

	/** A band with no market benchmark against it — the common case. */
	public BandBoundaries(Money min, Money mid, Money max) {
		this(min, mid, max, null);
	}

}
