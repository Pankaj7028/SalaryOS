package com.acme.salaryos.change.service;

/** 409: only a pending change awaits a decision — approving or rejecting anything else is a stale action. */
public class ChangeNotPendingException extends RuntimeException {

	public ChangeNotPendingException() {
		super("This change is not awaiting approval.");
	}

}
