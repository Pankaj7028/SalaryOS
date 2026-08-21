package com.acme.salaryos.change.service;

import java.util.UUID;

/** 409 (backend doc §8's exact copy, FR-5.6): one non-terminal change per employee at a time. */
public class OpenChangeAlreadyExistsException extends RuntimeException {

	private final UUID openChangeId;

	public OpenChangeAlreadyExistsException(UUID openChangeId) {
		super("A change for this employee is already awaiting approval.");
		this.openChangeId = openChangeId;
	}

	public UUID getOpenChangeId() {
		return openChangeId;
	}

}
