package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.dto.UpdateUserRequest;
import com.acme.salaryos.auth.dto.UserSummaryResponse;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.auth.service.LastActiveHrAdminException;
import com.acme.salaryos.auth.service.UserAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P8.1's own Verify clause: deactivating the last HR Admin is refused. {@code UserAdminService}'s
 * guard counts EVERY row in {@code users} — genuinely untestable against the big shared
 * Testcontainers container every other change/analytics test class (and even {@code UserAdminTest}'s
 * own two methods, sharing one class-cached container) populates with its own HR_ADMIN fixtures.
 * Runs with a properties signature no other test class uses, so Spring caches a container just for
 * this one method — same technique {@code PostgresContainerIntegrationTest} already uses for its
 * own reason — meaning the two admins seeded below really are the only HR_ADMIN rows anywhere in
 * this database, and the count this test exercises is exact, not merely "probably right."
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema",
		"spring.jpa.properties.hibernate.order_inserts=true"
})
@Import(TestcontainersConfiguration.class)
class LastActiveHrAdminTest {

	@Autowired
	private UserAdminService userAdminService;
	@Autowired
	private UserRepository userRepository;

	@Test
	void deactivatingTheLastActiveHrAdminIsRefusedButANonLastOneSucceeds() {
		User adminA = userRepository.save(User.builder()
				.email("admin-a@acme.test").fullName("Admin A").passwordHash("{argon2}stub").role("HR_ADMIN").build());
		User adminB = userRepository.save(User.builder()
				.email("admin-b@acme.test").fullName("Admin B").passwordHash("{argon2}stub").role("HR_ADMIN").build());
		// A real row, not a random UUID -- AuditService.recordWrite's FK to users(id) needs one.
		User console = userRepository.save(User.builder()
				.email("console@acme.test").fullName("Console").passwordHash("{argon2}stub").role("HR_MANAGER").build());
		UUID actingAsSomeoneElse = console.getId();

		// Two active admins -- deactivating one is fine.
		UserSummaryResponse deactivated = userAdminService.update(
				adminA.getId(), new UpdateUserRequest("Admin A", "HR_ADMIN", "INACTIVE"), actingAsSomeoneElse);
		assertThat(deactivated.status()).isEqualTo("INACTIVE");

		// Now adminB is the only active HR_ADMIN left -- deactivating them is refused.
		assertThatThrownBy(() -> userAdminService.update(
				adminB.getId(), new UpdateUserRequest("Admin B", "HR_ADMIN", "INACTIVE"), actingAsSomeoneElse))
				.isInstanceOf(LastActiveHrAdminException.class);

		// Reassigning their role away from HR_ADMIN has the identical effect -- also refused.
		assertThatThrownBy(() -> userAdminService.update(
				adminB.getId(), new UpdateUserRequest("Admin B", "HR_MANAGER", "ACTIVE"), actingAsSomeoneElse))
				.isInstanceOf(LastActiveHrAdminException.class);

		// adminB is genuinely still the sole active HR_ADMIN -- neither rejected call took effect.
		User reloaded = userRepository.findById(adminB.getId()).orElseThrow();
		assertThat(reloaded.getRole()).isEqualTo("HR_ADMIN");
		assertThat(reloaded.getStatus()).isEqualTo("ACTIVE");
	}

}
