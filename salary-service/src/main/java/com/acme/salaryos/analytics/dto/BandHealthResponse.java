package com.acme.salaryos.analytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * P11.3. Bands were CRUD plus a compa-ratio denominator; nothing asked whether the structure
 * itself holds up. When the spreadsheets go away this is the first question a comp analyst asks.
 *
 * <p>Read-only over existing tables — no migration, no writes. No {@link FxBasis}: every figure is
 * in the band's own currency, so no normalisation happens here.
 *
 * <p>The counts are the headline; {@code rows} is the detail behind them.
 */
public record BandHealthResponse(
		LocalDate asAtDate,
		int inForceBands,
		int bandsWithNoIncumbents,
		int bandsWithGapToPreviousLevel,
		int staleBands,
		int staleAfterMonths,
		List<BandHealthRow> rows) {
}
