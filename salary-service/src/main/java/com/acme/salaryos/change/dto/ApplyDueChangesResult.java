package com.acme.salaryos.change.dto;

import java.util.List;
import java.util.UUID;

/** FR-5.7: what the job (or its manual trigger) actually did — a count and, on the rare failure, which change and why. */
public record ApplyDueChangesResult(int due, int applied, List<Failure> failures) {

	public record Failure(UUID changeId, String reason) {
	}

}
