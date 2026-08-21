package com.acme.salaryos.band.service;

/** 409: only the current in-force version of a band can be versioned — a superseded one is history. */
public class BandNotOpenException extends RuntimeException {

	public BandNotOpenException() {
		super("This band has already been superseded and can't be versioned directly.");
	}

}
