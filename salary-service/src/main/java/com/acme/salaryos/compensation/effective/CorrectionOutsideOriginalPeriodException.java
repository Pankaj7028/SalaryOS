package com.acme.salaryos.compensation.effective;

/** 400: a correction re-dates within the period it is fixing, never outside it — that is a new period, not a fix. */
public class CorrectionOutsideOriginalPeriodException extends RuntimeException {

	public CorrectionOutsideOriginalPeriodException() {
		super("A correction's effective date must fall within the period it corrects.");
	}

}
