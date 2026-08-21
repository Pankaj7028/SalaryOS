package com.acme.salaryos.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** FR-1.5. A full replace, not a partial patch — every field required, matching
 * {@code EmployeeUpdateRequest}'s own convention (P4.2): every write states the whole intended
 * state, so a client can't accidentally leave a field unspecified and have it silently untouched. */
public record UpdateUserRequest(@NotBlank String fullName, @NotBlank String role, @NotBlank String status) {
}
