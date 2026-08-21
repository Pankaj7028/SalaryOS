package com.acme.salaryos.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The refresh token itself is a high-entropy random secret, not a JWT — only its SHA-256 hash is
 * ever stored (like {@code password_reset_tokens.token_hash}), so a leaked database dump doesn't
 * hand out live sessions.
 */
final class RefreshTokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private RefreshTokens() {
	}

	static String generate() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is guaranteed to be available on every JVM", e);
		}
	}

}
