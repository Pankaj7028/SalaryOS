package com.acme.salaryos.analytics.dto;

/**
 * How much a failing check matters. Not a count of rows — a judgement about what a failure means,
 * so the console can sort by what actually needs attention rather than by which check happens to
 * have the biggest number.
 */
public enum DataHealthSeverity {

	/** A figure somewhere is wrong or missing. Someone is being reported on incorrectly. */
	CRITICAL,

	/** Internally consistent but almost certainly not what was intended. Worth a look. */
	WARNING,

	/** Legitimate, but worth knowing about — usually a gap the product cannot fill by itself. */
	INFO
}
