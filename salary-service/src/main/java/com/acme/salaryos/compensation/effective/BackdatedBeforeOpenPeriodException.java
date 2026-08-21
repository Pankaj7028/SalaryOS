package com.acme.salaryos.compensation.effective;

import java.time.LocalDate;

/** 409 (salary-management-backend.md §8): the new period would not start after the existing one. */
public class BackdatedBeforeOpenPeriodException extends RuntimeException {

	public BackdatedBeforeOpenPeriodException(LocalDate openPeriodEffectiveFrom) {
		super("This employee already has pay recorded from " + openPeriodEffectiveFrom
				+ ". Choose a later effective date.");
	}

}
