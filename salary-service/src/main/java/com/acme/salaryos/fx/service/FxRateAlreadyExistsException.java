package com.acme.salaryos.fx.service;

import java.time.LocalDate;

public class FxRateAlreadyExistsException extends RuntimeException {

	public FxRateAlreadyExistsException(String baseCurrency, String quoteCurrency, LocalDate rateMonth) {
		super("A rate for " + baseCurrency + "→" + quoteCurrency + " in " + rateMonth + " already exists.");
	}

}
