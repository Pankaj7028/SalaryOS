package com.acme.salaryos.compensation.effective;

import java.time.YearMonth;

/** 422 (salary-management-backend.md §8): normalisation cannot proceed without a pinned rate. */
public class MissingFxRateException extends RuntimeException {

	public MissingFxRateException(String fromCurrency, String toCurrency, YearMonth month) {
		super("No exchange rate for " + fromCurrency + "→" + toCurrency + " in " + month
				+ ". Add the rate and try again.");
	}

}
