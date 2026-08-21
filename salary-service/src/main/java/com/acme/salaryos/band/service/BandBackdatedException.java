package com.acme.salaryos.band.service;

import java.time.LocalDate;

/** 409: a new version must start strictly after the version it replaces — the same day-boundary discipline as compensation periods. */
public class BandBackdatedException extends RuntimeException {

	public BandBackdatedException(LocalDate currentVersionEffectiveFrom) {
		super("This band's current version already starts " + currentVersionEffectiveFrom
				+ ". Choose a later effective date.");
	}

}
