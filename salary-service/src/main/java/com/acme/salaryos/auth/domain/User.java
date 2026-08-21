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

import java.time.Duration;
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

	private static final int MAX_FAILED_LOGINS = 5;
	private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

	/** FR-1.3: five consecutive failures locks the account for 15 minutes. */
	public void recordFailedLogin(Instant at) {
		this.failedLoginCount = this.failedLoginCount + 1;
		if (this.failedLoginCount >= MAX_FAILED_LOGINS) {
			this.lockedUntil = at.plus(LOCKOUT_DURATION);
		}
	}

	public void clearFailedLogins() {
		this.failedLoginCount = 0;
		this.lockedUntil = null;
	}

	public boolean isLocked(Instant at) {
		return lockedUntil != null && lockedUntil.isAfter(at);
	}

	/** FR-1.5: full name, role, and status together — the admin update endpoint states the whole
	 * intended record, never a partial patch. Callers (`UserAdminService`) are responsible for the
	 * "cannot change own role" / "last active HR Admin" guards; this method only mutates. */
	public void updateProfile(String fullName, String role, String status) {
		this.fullName = fullName;
		this.role = role;
		this.status = status;
	}

}
