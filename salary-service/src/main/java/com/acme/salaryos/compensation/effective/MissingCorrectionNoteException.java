package com.acme.salaryos.compensation.effective;

/** 400: a correction rewrites the record of what someone was paid, so it must say why. */
public class MissingCorrectionNoteException extends RuntimeException {

	public MissingCorrectionNoteException() {
		super("A correction requires a note explaining what was wrong and why.");
	}

}
