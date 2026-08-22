package com.acme.salaryos.audit.dto;

import java.time.Instant;
import java.util.UUID;

/** FR-7.4: actor identity carried alongside the row so the screen never joins client-side. */
public record AuditEventResponse(
		UUID id, Instant occurredAt, UUID actorUserId, String actorEmail, String actorFullName, String actorRole,
		String action, String entityType, UUID entityId, String beforeJson, String afterJson, String ip) {
}
