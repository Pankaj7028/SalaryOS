package com.acme.salaryos.auth.dto;

import java.util.UUID;

/** FR-1.4: id, name, email, role, and theme preference — nothing else. */
public record MeResponse(
		UUID id,
		String fullName,
		String email,
		String role,
		String themePreference) {
}
