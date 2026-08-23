package com.acme.salaryos.analytics.dto;

import java.time.LocalDate;

/**
 * FR-6.8's "FX rate month", reported honestly for an aggregate.
 *
 * <p>The criterion asks every analytics response to state the FX rate month it used. For a single
 * figure that is one date; for a population spanning thousands of employees it is not. Every
 * normalised figure in this system is pinned to whichever rate was in force when <em>that
 * employee's own record</em> was written (CLAUDE.md §6.4), so an aggregate is underpinned by many
 * rates across many months. Reporting one of them — "today's month", say — would imply this report
 * recomputed something at a live rate, which it never does, and would be the one thing FR-6.4
 * exists to prevent.
 *
 * <p>So the basis is reported as a span rather than a scalar. Together the four fields say: these
 * figures rest on {@code distinctRates} pinned rates covering {@code monthsSpanned} months between
 * {@code earliestMonth} and {@code latestMonth}, and nothing here was recomputed. That satisfies
 * FR-6.8's actual intent — <em>a number without its basis is not shippable</em> — without asserting
 * a fact that is not true.
 *
 * <p><strong>Only responses that carry money carry an {@code FxBasis}.</strong>
 * {@code HeadcountResponse} has no money in it at all, and {@code CompaRatioDistributionResponse}
 * reports {@code compa_ratio} — pay ÷ band mid, both already in the same currency, so no FX enters
 * it. Attaching a basis to either would fabricate one for a figure that has none.
 *
 * <p>All four fields are {@code null}/zero-safe for an empty population: a report over no employees
 * rests on no rates, and says so, rather than failing.
 */
public record FxBasis(
		int distinctRates,
		int monthsSpanned,
		LocalDate earliestMonth,
		LocalDate latestMonth) {

	/** The basis for a population that turned out to be empty — no rates, no span. */
	public static FxBasis empty() {
		return new FxBasis(0, 0, null, null);
	}
}
