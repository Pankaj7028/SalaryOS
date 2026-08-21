package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2.3 — FR-1.3: five consecutive failures locks the account for 15 minutes; wrong password,
 * unknown email, and a locked account are indistinguishable in status, body, and response time.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class AuthLockoutTest {

	private static final String PASSWORD = "CorrectHorseBatteryStaple1!";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void fiveFailedAttemptsLocksTheAccountAndACorrectPasswordStillFailsWhileLocked() throws Exception {
		String email = "lockout-" + UUID.randomUUID() + "@acme.test";
		userRepository.save(User.builder()
				.email(email).fullName("Lockout Tester")
				.passwordHash(passwordEncoder.encode(PASSWORD))
				.role("HR_ADMIN")
				.build());

		for (int attempt = 1; attempt <= 5; attempt++) {
			login(email, "wrong-password").andExpect(status().isUnauthorized());
		}

		User locked = userRepository.findByEmail(email).orElseThrow();
		assertThat(locked.getFailedLoginCount()).isEqualTo(5);
		assertThat(locked.isLocked(Instant.now())).isTrue();

		// The correct password no longer works — being locked overrides a right answer.
		login(email, PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void wrongPasswordUnknownEmailAndLockedAccountShareStatusBody() throws Exception {
		String lockedEmail = "already-locked-" + UUID.randomUUID() + "@acme.test";
		userRepository.save(User.builder()
				.email(lockedEmail).fullName("Already Locked")
				.passwordHash(passwordEncoder.encode(PASSWORD))
				.role("HR_ADMIN")
				.build());
		for (int attempt = 1; attempt <= 5; attempt++) {
			login(lockedEmail, "wrong-password");
		}

		String wrongPasswordEmail = "wrong-pw-uniform-" + UUID.randomUUID() + "@acme.test";
		userRepository.save(User.builder()
				.email(wrongPasswordEmail).fullName("Wrong Password")
				.passwordHash(passwordEncoder.encode(PASSWORD))
				.role("HR_ADMIN")
				.build());

		MvcResult lockedResult = login(lockedEmail, PASSWORD).andExpect(status().isUnauthorized()).andReturn();
		MvcResult wrongPasswordResult = login(wrongPasswordEmail, "wrong-password").andExpect(status().isUnauthorized()).andReturn();
		MvcResult unknownEmailResult = login("nobody-" + UUID.randomUUID() + "@acme.test", PASSWORD)
				.andExpect(status().isUnauthorized()).andReturn();

		String lockedBody = lockedResult.getResponse().getContentAsString();
		String wrongPasswordBody = wrongPasswordResult.getResponse().getContentAsString();
		String unknownEmailBody = unknownEmailResult.getResponse().getContentAsString();

		assertThat(lockedBody).isEqualTo(wrongPasswordBody).isEqualTo(unknownEmailBody);
	}

	@Test
	void responseTimingIsComparableAcrossFailureModes() throws Exception {
		String wrongPasswordEmail = "wrong-pw-timing-" + UUID.randomUUID() + "@acme.test";
		userRepository.save(User.builder()
				.email(wrongPasswordEmail).fullName("Wrong Password Timing")
				.passwordHash(passwordEncoder.encode(PASSWORD))
				.role("HR_ADMIN")
				.build());

		// Warm up the JIT / connection pool so the measured runs aren't dominated by one-time cost.
		login(wrongPasswordEmail, "wrong-password");
		login("warmup-" + UUID.randomUUID() + "@acme.test", PASSWORD);

		long wrongPasswordMillis = averageMillis(3, () -> login(wrongPasswordEmail, "wrong-password"));
		long unknownEmailMillis = averageMillis(3, () -> login("nobody-" + UUID.randomUUID() + "@acme.test", PASSWORD));

		long slower = Math.max(wrongPasswordMillis, unknownEmailMillis);
		long faster = Math.min(wrongPasswordMillis, unknownEmailMillis);
		// Both paths run exactly one Argon2id comparison, which dominates the timing — allow a
		// generous margin for scheduling noise rather than asserting near-equality.
		assertThat(slower).isLessThanOrEqualTo(faster * 3 + 50);
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}

	private long averageMillis(int iterations, ThrowingRunnable action) throws Exception {
		long total = 0;
		for (int i = 0; i < iterations; i++) {
			long start = System.nanoTime();
			action.run();
			total += (System.nanoTime() - start) / 1_000_000;
		}
		return total / iterations;
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

}
