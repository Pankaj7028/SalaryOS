package com.acme.salaryos.auth.domain;

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

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

/** One row per issued refresh token; {@code jti} is checked on every request for revocation. */
@Entity
@Table(name = "user_sessions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSession {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID userId;

	private UUID jti;

	private String refreshTokenHash;

	/** Links the rotation chain: a replayed refresh token revokes every session in the family. */
	private UUID familyId;

	@CreationTimestamp
	private Instant issuedAt;

	private Instant expiresAt;

	private Instant revokedAt;

	private String userAgent;

	/** {@code inet} in the database — Hibernate maps {@code InetAddress} to it natively. */
	private InetAddress ip;

}
