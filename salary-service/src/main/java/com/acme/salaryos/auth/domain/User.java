package com.acme.salaryos.auth.domain;

import com.acme.salaryos.common.jdbc.CitextJdbcType;
import jakarta.persistence.Column;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** {@code citext} in the database — case-insensitive email lookups without a lower() index. */
	@org.hibernate.annotations.JdbcType(CitextJdbcType.class)
	private String email;

	private String fullName;

	private String passwordHash;

	/** {@code HR_ADMIN}, {@code HR_MANAGER}, {@code COMP_ANALYST}, {@code AUDITOR} — CLAUDE.md §4.3. */
	private String role;

	@Builder.Default
	private String status = "ACTIVE";

	@Builder.Default
	@Column(name = "failed_login_count")
	private int failedLoginCount = 0;

	private Instant lockedUntil;

	@Builder.Default
	private String themePreference = "SYSTEM";

	@CreationTimestamp
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

}
