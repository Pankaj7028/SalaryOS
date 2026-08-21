package com.acme.salaryos.change.service;

/** 403 (backend doc §8's exact copy, FR-5.5): the proposer can never approve their own proposal. */
public class SelfApprovalException extends RuntimeException {

	public SelfApprovalException() {
		super("You proposed this change, so someone else has to approve it.");
	}

}
