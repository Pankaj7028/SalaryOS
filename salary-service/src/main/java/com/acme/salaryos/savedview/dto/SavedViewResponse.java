package com.acme.salaryos.savedview.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code ownedByMe} is resolved per request rather than stored — the same row is "mine" to its
 * owner and "shared with me" to everyone else, and the UI needs to know which without being handed
 * the owner's identity. Only {@code ownerName} is exposed, never the owner's id or email: a picker
 * needs to say who shared a view, not who to go and look up.
 */
public record SavedViewResponse(
		UUID id,
		String name,
		String route,
		String queryString,
		boolean shared,
		boolean ownedByMe,
		String ownerName,
		Instant createdAt) {
}
