package com.acme.salaryos.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints and validates the access token (CLAUDE.md §4.2): HS256, claims {@code sub} (user id),
 * {@code role}, {@code iat}, {@code exp}, {@code jti} — nothing else. The token says who you are;
 * the database says what you may do, checked freshly on every request.
 */
@Service
public class JwtService {

	private final SecretKey signingKey;
	private final Duration sessionTtl;

	public JwtService(
			@Value("${app.jwt.signing-key}") String signingKey,
			@Value("${app.session.ttl}") Duration sessionTtl) {
		this.signingKey = Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8));
		this.sessionTtl = sessionTtl;
	}

	public MintedToken mint(UUID userId, String role) {
		String jti = UUID.randomUUID().toString();
		Instant now = Instant.now();
		Instant expiresAt = now.plus(sessionTtl);
		String token = Jwts.builder()
				.subject(userId.toString())
				.claim("role", role)
				.id(jti)
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(signingKey, Jwts.SIG.HS256)
				.compact();
		return new MintedToken(token, jti, expiresAt);
	}

	/** @throws io.jsonwebtoken.JwtException if the signature is invalid, or the token is expired/malformed. */
	public Claims validate(String token) {
		return Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public record MintedToken(String token, String jti, Instant expiresAt) {
	}

}
