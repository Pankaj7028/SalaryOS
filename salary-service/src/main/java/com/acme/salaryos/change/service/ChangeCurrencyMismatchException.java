package com.acme.salaryos.change.service;

/** 400: {@code compensation_changes} shares one currency column between current and new base — a currency change isn't representable as a "change" proposal. */
public class ChangeCurrencyMismatchException extends RuntimeException {

	public ChangeCurrencyMismatchException() {
		super("A change must be proposed in the employee's current pay currency.");
	}

}
