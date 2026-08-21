package com.acme.salaryos.auth.service;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.domain.UserSession;
import com.acme.salaryos.auth.dto.MeResponse;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.auth.repository.UserSessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Login, logout, and refresh rotation (CLAUDE.md §4). Wrong password and unknown email throw the
 * same exception with the same message — full timing uniformity (dummy-hash on a miss) and the
 * lockout counter land in P2.3.
 */
@Service
@Slf4j
public class AuthService {

	private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

	private final UserRepository userRepository;
	private final UserSessionRepository userSessionRepository;
	private final JwtService jwtService;
	private final PasswordEncoder passwordEncoder;
	private final Duration refreshTtl;

	public AuthService(
			UserRepository userRepository,
			UserSessionRepository userSessionRepository,
			JwtService jwtService,
			PasswordEncoder passwordEncoder,
			@Value("${app.refresh.ttl}") Duration refreshTtl) {
		this.userRepository = userRepository;
		this.userSessionRepository = userSessionRepository;
		this.jwtService = jwtService;
		this.passwordEncoder = passwordEncoder;
		this.refreshTtl = refreshTtl;
	}

	@Transactional
	public IssuedSession login(String email, String password, String remoteAddress, String userAgent) {
		User user = userRepository.findByEmail(email).orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));

		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
		}
		if (!"ACTIVE".equals(user.getStatus())) {
			throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
		}

		return issueSession(user, UUID.randomUUID(), remoteAddress, userAgent);
	}

	/**
	 * Rotates the refresh token: revokes the presented row and mints a new one in the same
	 * family. Presenting an already-revoked token is the signature of a stolen cookie — the
	 * entire family is revoked and the caller is forced back to login (CLAUDE.md §4.4).
	 *
	 * <p>{@code noRollbackFor}: the reuse-detected path revokes the family and then throws to
	 * report the failure — without this, Spring's default rollback-on-RuntimeException would undo
	 * the very revocation the throw is reporting.
	 */
	@Transactional(noRollbackFor = BadCredentialsException.class)
	public IssuedSession refresh(String rawRefreshToken, String remoteAddress, String userAgent) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			throw new BadCredentialsException("Missing refresh token");
		}
		String hash = RefreshTokens.hash(rawRefreshToken);
		UserSession session = userSessionRepository.findByRefreshTokenHash(hash)
				.orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

		if (session.isRevoked()) {
			revokeFamily(session.getFamilyId());
			log.warn("Refresh token reuse detected for family {}; session family revoked", session.getFamilyId());
			throw new BadCredentialsException("Refresh token already used; session revoked");
		}
		if (session.getExpiresAt().isBefore(Instant.now())) {
			throw new BadCredentialsException("Refresh token expired");
		}

		session.revoke(Instant.now());
		userSessionRepository.save(session);

		User user = userRepository.findById(session.getUserId())
				.orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
		return issueSession(user, session.getFamilyId(), remoteAddress, userAgent);
	}

	@Transactional
	public void logout(String sessionToken) {
		if (sessionToken == null || sessionToken.isBlank()) {
			return;
		}
		try {
			Claims claims = jwtService.validate(sessionToken);
			UUID jti = UUID.fromString(claims.getId());
			userSessionRepository.findByJti(jti).ifPresent(session -> {
				session.revoke(Instant.now());
				userSessionRepository.save(session);
			});
		}
		catch (JwtException | IllegalArgumentException alreadyInvalid) {
			// nothing to revoke
		}
	}

	public MeResponse me(UUID userId) {
		User user = userRepository.findById(userId).orElseThrow(() -> new BadCredentialsException("Unknown session"));
		return new MeResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getThemePreference());
	}

	private IssuedSession issueSession(User user, UUID familyId, String remoteAddress, String userAgent) {
		JwtService.MintedToken accessToken = jwtService.mint(user.getId(), user.getRole());
		String rawRefreshToken = RefreshTokens.generate();
		Instant refreshExpiresAt = Instant.now().plus(refreshTtl);

		UserSession session = UserSession.builder()
				.userId(user.getId())
				.jti(UUID.fromString(accessToken.jti()))
				.refreshTokenHash(RefreshTokens.hash(rawRefreshToken))
				.familyId(familyId)
				.expiresAt(refreshExpiresAt)
				.userAgent(userAgent)
				.ip(parseAddress(remoteAddress))
				.build();
		userSessionRepository.save(session);

		return new IssuedSession(accessToken.token(), accessToken.expiresAt(), rawRefreshToken, refreshExpiresAt);
	}

	private void revokeFamily(UUID familyId) {
		List<UserSession> openSessions = userSessionRepository.findByFamilyIdAndRevokedAtIsNull(familyId);
		Instant now = Instant.now();
		openSessions.forEach(session -> session.revoke(now));
		userSessionRepository.saveAll(openSessions);
	}

	private InetAddress parseAddress(String remoteAddress) {
		if (remoteAddress == null) {
			return null;
		}
		try {
			return InetAddress.getByName(remoteAddress);
		}
		catch (UnknownHostException notAResolvableAddress) {
			return null;
		}
	}

	public record IssuedSession(String accessToken, Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt) {
	}

}
