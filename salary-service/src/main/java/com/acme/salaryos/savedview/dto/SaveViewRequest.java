package com.acme.salaryos.savedview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code queryString} is the raw {@code searchParams} the screen already puts in the URL
 * (CLAUDE.md §9) — no leading {@code ?}. It is stored verbatim and replayed verbatim; it is never
 * parsed into a query here, because the endpoint it is replayed against is what enforces the
 * guardrails.
 */
public record SaveViewRequest(
		@NotBlank @Size(max = 80) String name,
		@NotBlank @Size(max = 200) String route,
		@Size(max = 2000) String queryString,
		boolean shared) {

	/** A view with no filters is legitimate ("all employees") — normalise null to empty. */
	public SaveViewRequest {
		queryString = queryString == null ? "" : queryString;
	}
}
