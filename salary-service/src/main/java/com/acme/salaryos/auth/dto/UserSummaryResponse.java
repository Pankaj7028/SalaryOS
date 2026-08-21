package com.acme.salaryos.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record UserSummaryResponse(UUID id, String email, String fullName, String role, String status, Instant createdAt) {
}
