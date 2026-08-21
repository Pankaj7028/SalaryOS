package com.acme.salaryos.common.paging;

public class InvalidCursorException extends RuntimeException {

	public InvalidCursorException(String cursor) {
		super("Invalid or corrupted page cursor.");
	}

}
