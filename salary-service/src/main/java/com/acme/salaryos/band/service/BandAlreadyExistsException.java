package com.acme.salaryos.band.service;

import java.time.LocalDate;

/** 409: this (job level × country) already has an in-force band — {@code create} is for a combination that has never had one. */
public class BandAlreadyExistsException extends RuntimeException {

	public BandAlreadyExistsException(LocalDate inForceSince) {
		super("A band for this level and country already exists, effective " + inForceSince
				+ ". Use PATCH to version it instead.");
	}

}
