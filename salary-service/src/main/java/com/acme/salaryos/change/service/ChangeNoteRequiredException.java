package com.acme.salaryos.change.service;

/** 400 (FR-5.2/FR-5.4): a note is mandatory for a correction, and for any proposal landing outside the band. */
public class ChangeNoteRequiredException extends RuntimeException {

	public ChangeNoteRequiredException(String reason) {
		super("A note is required " + reason + ".");
	}

}
