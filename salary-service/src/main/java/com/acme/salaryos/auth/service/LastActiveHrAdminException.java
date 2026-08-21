package com.acme.salaryos.auth.service;

/**
 * FR-1.5: "the last active HR Admin cannot be deactivated." Also blocks reassigning the last
 * active HR Admin's role away from HR_ADMIN — losing HR_ADMIN status has the identical effect
 * (nobody left who can manage users) as deactivation, so the same guard covers both, not just the
 * literal status field the requirement names.
 */
public class LastActiveHrAdminException extends RuntimeException {

	public LastActiveHrAdminException() {
		super("This is the last active HR Admin — deactivate or reassign someone else first.");
	}

}
