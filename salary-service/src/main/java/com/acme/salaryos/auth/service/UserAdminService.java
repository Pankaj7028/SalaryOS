package com.acme.salaryos.auth.service;

import com.acme.salaryos.audit.AuditService;
import com.acme.salaryos.auth.domain.PasswordResetToken;
import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.dto.CreateUserRequest;
import com.acme.salaryos.auth.dto.ResetTokenResponse;
import com.acme.salaryos.auth.dto.UpdateUserRequest;
import com.acme.salaryos.auth.dto.UserSummaryResponse;
import com.acme.salaryos.auth.repository.PasswordResetTokenRepository;
import com.acme.salaryos.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/** FR-1.5 / FR-1.6: users and roles admin. */
@Service
public class UserAdminService {

	private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository resetTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final AuditService auditService;

	public UserAdminService(
			UserRepository userRepository, PasswordResetTokenRepository resetTokenRepository,
			PasswordEncoder passwordEncoder, Clock clock, AuditService auditService) {
		this.userRepository = userRepository;
		this.resetTokenRepository = resetTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.auditService = auditService;
	}

	public List<UserSummaryResponse> list() {
		return userRepository.findAll().stream().map(this::toResponse).toList();
	}

	/** No password field on the request — a random, never-known-to-anyone secret becomes the
	 * initial hash, exactly as unusable as a locked account until a reset token is issued. */
	@Transactional
	public UserSummaryResponse create(CreateUserRequest request, UUID currentUserId) {
		byte[] randomSecret = new byte[32];
		RANDOM.nextBytes(randomSecret);
		String unusablePassword = Base64.getEncoder().encodeToString(randomSecret);

		// saveAndFlush, not save: @CreationTimestamp is a Hibernate-generated value populated at
		// flush time, not by the builder — returning the un-flushed entity's createdAt here would
		// silently respond with null instead of the real timestamp just written.
		User user = userRepository.saveAndFlush(User.builder()
				.email(request.email())
				.fullName(request.fullName())
				.passwordHash(passwordEncoder.encode(unusablePassword))
				.role(request.role())
				.build());
		auditService.recordWrite(currentUserId, "CREATE_USER", "USER", user.getId(), null, toResponse(user));
		return toResponse(user);
	}

	@Transactional
	public UserSummaryResponse update(UUID id, UpdateUserRequest request, UUID currentUserId) {
		User user = userRepository.findById(id).orElseThrow(NoSuchElementException::new);

		if (id.equals(currentUserId) && !request.role().equals(user.getRole())) {
			throw new CannotChangeOwnRoleException();
		}

		boolean losingHrAdmin = "HR_ADMIN".equals(user.getRole())
				&& ("INACTIVE".equals(request.status()) || !"HR_ADMIN".equals(request.role()));
		if (losingHrAdmin && countOtherActiveHrAdmins(id) == 0) {
			throw new LastActiveHrAdminException();
		}

		UserSummaryResponse before = toResponse(user);
		user.updateProfile(request.fullName(), request.role(), request.status());
		UserSummaryResponse after = toResponse(userRepository.save(user));
		auditService.recordWrite(currentUserId, "UPDATE_USER", "USER", id, before, after);
		return after;
	}

	/** FR-1.6: single-use, 30-minute, admin-issued — the raw token is returned exactly once and
	 * never persisted; only its SHA-256 hash is (the same pattern refresh tokens use). */
	@Transactional
	public ResetTokenResponse issueResetToken(UUID id, UUID currentUserId) {
		User user = userRepository.findById(id).orElseThrow(NoSuchElementException::new);
		String rawToken = RefreshTokens.generate();
		Instant expiresAt = Instant.now(clock).plus(RESET_TOKEN_TTL);

		resetTokenRepository.save(PasswordResetToken.builder()
				.userId(user.getId())
				.tokenHash(RefreshTokens.hash(rawToken))
				.expiresAt(expiresAt)
				.build());

		// Never the raw token in the audit trail — only that one was issued, and when.
		auditService.recordWrite(currentUserId, "ISSUE_RESET_TOKEN", "USER", id, null, Map.of("expiresAt", expiresAt.toString()));

		return new ResetTokenResponse(rawToken, expiresAt);
	}

	private int countOtherActiveHrAdmins(UUID excludingId) {
		return (int) userRepository.findAll().stream()
				.filter(u -> !u.getId().equals(excludingId))
				.filter(u -> "HR_ADMIN".equals(u.getRole()) && "ACTIVE".equals(u.getStatus()))
				.count();
	}

	private UserSummaryResponse toResponse(User user) {
		return new UserSummaryResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getStatus(), user.getCreatedAt());
	}

}
