package com.acme.salaryos.auth.dto;

import java.time.Instant;

/** FR-1.6: the raw token, shown exactly once — only its hash is ever persisted, so this response
 * is the only place it can be read back. */
public record ResetTokenResponse(String token, Instant expiresAt) {
}
