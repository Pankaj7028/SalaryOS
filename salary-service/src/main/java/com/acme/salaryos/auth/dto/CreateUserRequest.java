package com.acme.salaryos.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** FR-1.5. No password field — a brand-new account starts with a random, unusable password hash
 * (nobody, including the admin, ever knows it) and is unlocked by immediately issuing a reset
 * token, the same mechanism an existing user's forgotten password goes through. */
public record CreateUserRequest(@NotBlank String email, @NotBlank String fullName, @NotBlank String role) {
}
