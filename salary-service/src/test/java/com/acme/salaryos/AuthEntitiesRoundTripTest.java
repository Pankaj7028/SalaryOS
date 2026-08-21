package com.acme.salaryos;

import com.acme.salaryos.auth.domain.PasswordResetToken;
import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.domain.UserSession;
import com.acme.salaryos.auth.repository.PasswordResetTokenRepository;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.auth.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P1.9: JPA round-trip for the auth module's entities. */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class AuthEntitiesRoundTripTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserSessionRepository userSessionRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Test
	void userRoundTrips() {
		User saved = userRepository.save(User.builder()
				.email("roundtrip-user@acme.test")
				.fullName("Round Trip")
				.passwordHash("{argon2}stub")
				.role("HR_ADMIN")
				.build());

		User loaded = userRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getEmail()).isEqualTo("roundtrip-user@acme.test");
		assertThat(loaded.getRole()).isEqualTo("HR_ADMIN");
		assertThat(loaded.getStatus()).isEqualTo("ACTIVE");
		assertThat(loaded.getThemePreference()).isEqualTo("SYSTEM");
		assertThat(loaded.getCreatedAt()).isNotNull();
		assertThat(loaded.getUpdatedAt()).isNotNull();
	}

	@Test
	void userSessionRoundTripsIncludingInetIp() throws java.net.UnknownHostException {
		User user = userRepository.save(User.builder()
				.email("roundtrip-session-user@acme.test")
				.fullName("Session Owner")
				.passwordHash("{argon2}stub")
				.role("HR_MANAGER")
				.build());

		UserSession saved = userSessionRepository.save(UserSession.builder()
				.userId(user.getId())
				.jti(UUID.randomUUID())
				.refreshTokenHash("hash")
				.familyId(UUID.randomUUID())
				.expiresAt(Instant.now().plusSeconds(3600))
				.ip(java.net.InetAddress.getByName("203.0.113.7"))
				.build());

		UserSession loaded = userSessionRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getUserId()).isEqualTo(user.getId());
		assertThat(loaded.getIp().getHostAddress()).isEqualTo("203.0.113.7");
		assertThat(loaded.getIssuedAt()).isNotNull();
	}

	@Test
	void passwordResetTokenRoundTrips() {
		User user = userRepository.save(User.builder()
				.email("roundtrip-reset-user@acme.test")
				.fullName("Reset Owner")
				.passwordHash("{argon2}stub")
				.role("HR_ADMIN")
				.build());

		PasswordResetToken saved = passwordResetTokenRepository.save(PasswordResetToken.builder()
				.userId(user.getId())
				.tokenHash("token-hash")
				.expiresAt(Instant.now().plusSeconds(1800))
				.build());

		PasswordResetToken loaded = passwordResetTokenRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getUserId()).isEqualTo(user.getId());
		assertThat(loaded.getUsedAt()).isNull();
	}

}
