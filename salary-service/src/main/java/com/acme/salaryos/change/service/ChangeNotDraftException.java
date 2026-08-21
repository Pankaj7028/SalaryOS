package com.acme.salaryos.change.service;

/** 409: only a draft can be edited, submitted, or discarded — once it's PENDING or later, it's out of the proposer's hands. */
public class ChangeNotDraftException extends RuntimeException {

	public ChangeNotDraftException() {
		super("This change is no longer a draft and can't be edited, submitted, or discarded.");
	}

}
