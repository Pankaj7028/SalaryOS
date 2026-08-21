package com.acme.salaryos.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only (CLAUDE.md §6.7, FR-7.3) — the {@code salaryos_app} database role has no
 * UPDATE/DELETE grant on this table (V8); {@code AuditImmutabilityTest} (P8.2) asserts it.
 */
@Entity
@Table(name = "audit_events")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@CreationTimestamp
	private Instant occurredAt;

	private UUID actorUserId;

	private String actorRole;

	private String action;

	private String entityType;

	private UUID entityId;

	@JdbcTypeCode(SqlTypes.JSON)
	private String beforeJson;

	@JdbcTypeCode(SqlTypes.JSON)
	private String afterJson;

	private UUID requestId;

	/** {@code inet} in the database — Hibernate maps {@code InetAddress} to it natively. */
	private InetAddress ip;

}
