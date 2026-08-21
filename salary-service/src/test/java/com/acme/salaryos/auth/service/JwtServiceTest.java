package com.acme.salaryos.auth.service;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P2.1: token validity, expiry, and tampering — no Spring context needed. */
class JwtServiceTest {

	private static final String SIGNING_KEY = "unit-test-signing-key-at-least-32-bytes-long";

	@Test
	void mintedTokenValidatesWithTheExpectedClaims() {
		JwtService jwtService = new JwtService(SIGNING_KEY, Duration.ofMinutes(20));
		UUID userId = UUID.randomUUID();

		JwtService.MintedToken minted = jwtService.mint(userId, "HR_ADMIN");
		Claims claims = jwtService.validate(minted.token());

		assertThat(claims.getSubject()).isEqualTo(userId.toString());
		assertThat(claims.get("role", String.class)).isEqualTo("HR_ADMIN");
		assertThat(claims.getId()).isEqualTo(minted.jti());
		assertThat(claims.getExpiration()).isAfter(new java.util.Date());
	}

	@Test
	void expiredTokenFailsValidation() {
		JwtService jwtService = new JwtService(SIGNING_KEY, Duration.ofMillis(1));
		JwtService.MintedToken minted = jwtService.mint(UUID.randomUUID(), "HR_MANAGER");

		await(50);

		assertThatThrownBy(() -> jwtService.validate(minted.token()))
				.isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	void tamperedTokenFailsSignatureValidation() {
		JwtService jwtService = new JwtService(SIGNING_KEY, Duration.ofMinutes(20));
		JwtService.MintedToken minted = jwtService.mint(UUID.randomUUID(), "COMP_ANALYST");

		String[] parts = minted.token().split("\\.");
		// Flip the role claim inside the payload segment without re-signing.
		String tamperedPayload = new StringBuilder(parts[1]).reverse().toString();
		String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

		assertThatThrownBy(() -> jwtService.validate(tamperedToken))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void tokenSignedWithADifferentKeyFailsValidation() {
		JwtService signer = new JwtService(SIGNING_KEY, Duration.ofMinutes(20));
		JwtService verifier = new JwtService("a-completely-different-signing-key-32-bytes!", Duration.ofMinutes(20));

		JwtService.MintedToken minted = signer.mint(UUID.randomUUID(), "AUDITOR");

		assertThatThrownBy(() -> verifier.validate(minted.token()))
				.isInstanceOf(JwtException.class);
	}

	private void await(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
