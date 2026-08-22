package com.acme.salaryos.employee.service;

/** "Set initial compensation" is only valid once, before an employee's first-ever ledger row
 * exists — every change after that goes through the propose/approve/apply lifecycle instead. */
public class EmployeeAlreadyHasCompensationException extends RuntimeException {

	public EmployeeAlreadyHasCompensationException() {
		super("This employee already has compensation history — propose a change instead of setting initial pay again.");
	}

}
