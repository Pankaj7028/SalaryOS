package com.acme.salaryos.change.service;

import java.time.LocalDate;

/** 409: {@code ApplyDueChangesJob}'s own candidate query already excludes this, so this only fires on a direct/manual apply call with a future-dated change. */
public class ChangeNotDueException extends RuntimeException {

	public ChangeNotDueException(LocalDate effectiveDate) {
		super("This change is not due yet — its effective date is " + effectiveDate + ".");
	}

}
