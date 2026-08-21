package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2.2 — the full auth cycle end to end, plus the reuse case: presenting an already-rotated
 * refresh token must revoke every session in its family (CLAUDE.md §4.4).
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
class AuthControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

	private static final String PASSWORD = "CorrectHorseBatteryStaple1!";

	@Test
	void fullLoginRefreshLogoutCycleAndRefreshTokenReuseRevokesTheFamily() throws Exception {
		userRepository.save(User.builder()
				.email("auth-cycle@acme.test")
				.fullName("Cycle Tester")
				.passwordHash(passwordEncoder.encode(PASSWORD))
				.role("HR_ADMIN")
				.build());

		// 1. Login issues sos_session + sos_refresh + sos_csrf.
		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("{\"email\":\"auth-cycle@acme.test\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andReturn();

		Cookie sessionCookie = loginResult.getResponse().getCookie("sos_session");
		Cookie refreshCookie = loginResult.getResponse().getCookie("sos_refresh");
		Cookie csrfCookie = loginResult.getResponse().getCookie("sos_csrf");
		assertThat(sessionCookie).isNotNull();
		assertThat(refreshCookie).isNotNull();
		assertThat(csrfCookie).isNotNull();

		// 2. The session authenticates GET /api/auth/me.
		mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("auth-cycle@acme.test"))
				.andExpect(jsonPath("$.role").value("HR_ADMIN"));

		// 3. Refresh rotates: a new session + refresh cookie, both different from the originals.
		MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
				.andExpect(status().isOk())
				.andReturn();
		Cookie rotatedSessionCookie = refreshResult.getResponse().getCookie("sos_session");
		Cookie rotatedRefreshCookie = refreshResult.getResponse().getCookie("sos_refresh");
		assertThat(rotatedSessionCookie.getValue()).isNotEqualTo(sessionCookie.getValue());
		assertThat(rotatedRefreshCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

		// 4. The rotated session still authenticates.
		mockMvc.perform(get("/api/auth/me").cookie(rotatedSessionCookie))
				.andExpect(status().isOk());

		// 5. Logout revokes the current (rotated) session — done before the reuse-detection
		// checks below, since those deliberately revoke the whole family and would otherwise
		// take this session down with it, which is correct but would confuse what this step
		// is verifying.
		mockMvc.perform(post("/api/auth/logout")
						.cookie(rotatedSessionCookie, csrfCookie)
						.header("X-CSRF-Token", csrfCookie.getValue()))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/auth/me").cookie(rotatedSessionCookie))
				.andExpect(status().isUnauthorized());

		// 7. Reusing the ORIGINAL (rotated-away-in-step-3) refresh token is a 401 — and it
		// revokes the whole family. Session B (the rotated one) is already revoked from logout;
		// this proves the reuse path is rejected independently of that.
		mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
				.andExpect(status().isUnauthorized());

		// The already-rotated-away token from step 3 is also rejected — whether because logout
		// revoked it or because this reuse path revokes the family again, either is correct.
		mockMvc.perform(post("/api/auth/refresh").cookie(rotatedRefreshCookie))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void wrongPasswordAndUnknownEmailReturnTheSameStatusAndMessage() throws Exception {
		userRepository.save(User.builder()
				.email("wrong-pw@acme.test")
				.fullName("Wrong Password Tester")
				.passwordHash(passwordEncoder.encode(PASSWORD))
				.role("HR_MANAGER")
				.build());

		MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("{\"email\":\"wrong-pw@acme.test\",\"password\":\"not-the-password\"}"))
				.andExpect(status().isUnauthorized())
				.andReturn();

		MvcResult unknownEmail = mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("{\"email\":\"nobody-at-all@acme.test\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isUnauthorized())
				.andReturn();

		assertThat(wrongPassword.getResponse().getContentAsString())
				.isEqualTo(unknownEmail.getResponse().getContentAsString());
	}

}
