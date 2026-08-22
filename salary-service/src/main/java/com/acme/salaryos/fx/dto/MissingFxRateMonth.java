package com.acme.salaryos.fx.dto;

import java.time.LocalDate;

/** A (currency, month) pin that normalisation would need but {@code fx_rates} doesn't have yet. */
public record MissingFxRateMonth(String baseCurrency, String quoteCurrency, LocalDate rateMonth) {
}
