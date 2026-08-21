package com.acme.salaryos.change.service;

/** 400: a "change" needs pay to change from — an employee with none needs an initial hire record, not a proposal. */
public class NoCurrentCompensationException extends RuntimeException {

	public NoCurrentCompensationException() {
		super("This employee has no current compensation to propose a change against.");
	}

}
