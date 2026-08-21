package com.acme.salaryos;

import com.acme.salaryos.auth.domain.PasswordResetToken;
import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.dto.CreateUserRequest;
import com.acme.salaryos.auth.dto.ResetTokenResponse;
import com.acme.salaryos.auth.dto.UpdateUserRequest;
import com.acme.salaryos.auth.dto.UserSummaryResponse;
import com.acme.salaryos.auth.repository.PasswordResetTokenRepository;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.auth.service.CannotChangeOwnRoleException;
import com.acme.salaryos.auth.service.UserAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P8.1: self-role-change and create/reset-token, both of which tolerate the big shared
 * Testcontainers container's other HR_ADMIN fixtures fine (neither counts rows). The last-active-
 * HR-Admin scenario needs real isolation and lives in its own {@code LastActiveHrAdminTest} instead
 * — see that class's own javadoc for why.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class UserAdminTest {

	@Autowired
	private UserAdminService userAdminService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordResetTokenRepository resetTokenRepository;

	@Test
	void aUserCannotChangeTheirOwnRoleEvenIfTheyAreAnHrAdmin() {
		User self = userRepository.save(User.builder()
				.email("self@acme.test").fullName("Self").passwordHash("{argon2}stub").role("HR_MANAGER").build());

		assertThatThrownBy(() -> userAdminService.update(
				self.getId(), new UpdateUserRequest("Self", "COMP_ANALYST", "ACTIVE"), self.getId()))
				.isInstanceOf(CannotChangeOwnRoleException.class);

		// Changing anything OTHER than the role, on yourself, is fine.
		UserSummaryResponse renamed = userAdminService.update(
				self.getId(), new UpdateUserRequest("Self Renamed", "HR_MANAGER", "ACTIVE"), self.getId());
		assertThat(renamed.fullName()).isEqualTo("Self Renamed");
	}

	@Test
	void createIssuesAnUnusablePasswordAndTheResetTokenPersistsOnlyItsHash() throws Exception {
		User actor = userRepository.save(User.builder()
				.email("actor-create@acme.test").fullName("Actor").passwordHash("{argon2}stub").role("HR_ADMIN").build());

		UserSummaryResponse created = userAdminService.create(
				new CreateUserRequest("new-hire@acme.test", "New Hire", "COMP_ANALYST"), actor.getId());
		assertThat(created.status()).isEqualTo("ACTIVE");
		// A real, previously-hit bug: @CreationTimestamp is populated at flush time, not by the
		// builder -- returning an un-flushed entity's createdAt here was silently null.
		assertThat(created.createdAt()).isNotNull();
		User stored = userRepository.findById(created.id()).orElseThrow();
		assertThat(stored.getPasswordHash()).isNotBlank();

		ResetTokenResponse reset = userAdminService.issueResetToken(created.id(), actor.getId());
		assertThat(reset.token()).isNotBlank();
		assertThat(reset.expiresAt()).isAfter(Instant.now());
		assertThat(reset.expiresAt()).isBefore(Instant.now().plusSeconds(31 * 60));

		PasswordResetToken savedToken = resetTokenRepository.findAll().stream()
				.filter(t -> t.getUserId().equals(created.id()))
				.findFirst().orElseThrow();
		assertThat(savedToken.getTokenHash()).isNotEqualTo(reset.token());
		assertThat(savedToken.getTokenHash()).isEqualTo(sha256Hex(reset.token()));
		assertThat(savedToken.getUsedAt()).isNull();
	}

	private String sha256Hex(String raw) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
	}

}
