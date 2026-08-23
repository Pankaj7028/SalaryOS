package com.acme.salaryos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A regression test for a bug found in QA during P10.5, which affected <b>every</b> validated
 * endpoint in the service rather than any one feature.
 *
 * <p>A bean-validation failure used to reach the client as {@code 401 "Authentication required"}.
 * Spring's default resolver sets a bare 400, the container forwards to {@code /error}, that ERROR
 * dispatch does not re-run the session-cookie filter (it is a {@code OncePerRequestFilter}), and so
 * {@code anyRequest().authenticated()} sees an empty {@code SecurityContext} and the entry point
 * overwrites the 400 with a 401. A signed-in user who typed a bad value was told they were signed
 * out, and never saw the message that would have told them what to fix.
 *
 * <p>The assertion that matters is {@code isBadRequest()} — 401 here means the regression is back.
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
class ValidationFailureIsNotUnauthorizedTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	/** Double-submit CSRF (CLAUDE.md §4.5): the cookie value echoed in the header. */
	private static final String CSRF = "test-csrf-token";

	@Test
	void anInvalidBodyFromASignedInUserIs400AndNot401() throws Exception {
		mockMvc.perform(post("/api/changes")
						.with(authAs(seedUser()))
						.cookie(new Cookie("sos_csrf", CSRF))
						.header("X-CSRF-Token", CSRF)
						.contentType(MediaType.APPLICATION_JSON)
						// No employeeId, which the DTO marks @NotNull.
						.content("{\"effectiveDate\":\"2027-01-01\",\"newBaseAmount\":100,"
								+ "\"currency\":\"USD\",\"changeReason\":\"MERIT\"}"))
				.andExpect(status().isBadRequest());
	}

	/** The point of returning 400 is that the message written on the constraint gets through. */
	@Test
	void theConstraintsOwnMessageReachesTheClient() throws Exception {
		mockMvc.perform(post("/api/changes/bulk-propose")
						.with(authAs(seedUser()))
						.cookie(new Cookie("sos_csrf", CSRF))
						.header("X-CSRF-Token", CSRF)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"employeeIds\":[\"" + UUID.randomUUID() + "\"],"
								+ "\"effectiveDate\":\"2027-01-01\",\"percentIncrease\":5000,"
								+ "\"changeReason\":\"MERIT\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(
						org.hamcrest.Matchers.containsString("not a bulk operation")))
				.andExpect(jsonPath("$.errors.percentIncrease").exists());
	}

	/** The fix must not weaken the real thing: an unauthenticated request is still 401. */
	@Test
	void anUnauthenticatedRequestIsStill401() throws Exception {
		mockMvc.perform(post("/api/changes")
						.cookie(new Cookie("sos_csrf", CSRF))
						.header("X-CSRF-Token", CSRF)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"effectiveDate\":\"2027-01-01\"}"))
				.andExpect(status().isUnauthorized());
	}

	private UUID seedUser() {
		jdbcTemplate.update("insert into salary_schema.users (id, email, full_name, password_hash, role) "
				+ "values (gen_random_uuid(), 'hr-admin-valid@acme.test', 'HR Admin', '{argon2}stub', 'HR_ADMIN') "
				+ "on conflict (email) do nothing");
		return jdbcTemplate.queryForObject(
				"select id from salary_schema.users where email = 'hr-admin-valid@acme.test'", UUID.class);
	}

	private static RequestPostProcessor authAs(UUID userId) {
		return SecurityMockMvcRequestPostProcessors.authentication(
				new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_HR_ADMIN"))));
	}

}
