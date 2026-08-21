package com.acme.salaryos.band.service;

/** 400: mirrors the `band_ordered` CHECK constraint (V4) with a message a human can act on. */
public class BandOrderingException extends RuntimeException {

	public BandOrderingException() {
		super("A band's minimum must be less than or equal to its midpoint, which must be less than or equal to its maximum.");
	}

}
