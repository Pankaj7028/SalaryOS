package com.acme.salaryos.auth.service;

/** FR-1.5: "a user cannot change their own role." */
public class CannotChangeOwnRoleException extends RuntimeException {

	public CannotChangeOwnRoleException() {
		super("You cannot change your own role.");
	}

}
