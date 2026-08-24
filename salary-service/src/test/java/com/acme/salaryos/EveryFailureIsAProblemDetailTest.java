package com.acme.salaryos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * `docs/salary-management-backend.md` §8: "Every failure is an RFC 7807 {@code ProblemDetail} with a
 * {@code detail} written for a human — the UI shows it directly."
 *
 * <p>Found in the QA sweep: that held for the service's own domain exceptions and for nothing else.
 * Spring's own MVC exceptions fell through to the container's default error body — which carries
 * {@code timestamp}, {@code error} and {@code path}, but no {@code detail} at all — so
 * {@code ApiError.problem} on the client was undefined and every malformed request produced generic
 * fallback copy instead of a sentence saying what to fix. The status codes were right the whole
 * time, which is why nothing looked broken.
 *
 * <p>Each case below is a shape a user can actually produce from the UI.
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
class EveryFailureIsAProblemDetailTest {

	private static final String CSRF = "test-csrf-token";

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbcTemplate;

	@Test
	void aBadIdInAPathExplainsItself() throws Exception {
		problemDetail(mockMvc.perform(get("/api/employees/not-a-uuid").with(authAs(seedUser()))), 400)
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("id")));
	}

	@Test
	void aNonNumericLimitExplainsItself() throws Exception {
		problemDetail(mockMvc.perform(get("/api/employees?limit=abc").with(authAs(seedUser()))), 400)
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("limit")));
	}

	/** An unknown enum value should say what the allowed values are, not just "bad request". */
	@Test
	void anUnknownEnumValueListsTheAllowedOnes() throws Exception {
		problemDetail(
				mockMvc.perform(get("/api/analytics/payroll-cost?basis=NONSENSE").with(authAs(seedUser()))), 400)
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("TOTAL_TARGET_CASH")));
	}

	@Test
	void anUnparseableDateExplainsTheFormat() throws Exception {
		problemDetail(mockMvc.perform(get("/api/employees/" + UUID.randomUUID()
				+ "/compensation/as-at?date=notadate").with(authAs(seedUser()))), 400)
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("YYYY-MM-DD")));
	}

	@Test
	void anUnreadableBodySaysSo() throws Exception {
		problemDetail(mockMvc.perform(post("/api/changes")
				.with(authAs(seedUser()))
				.cookie(new Cookie("sos_csrf", CSRF)).header("X-CSRF-Token", CSRF)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{not json")), 400)
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("JSON")));
	}

	/**
	 * This was a <b>500</b> on every importer in the service before the sweep. Submitting an upload
	 * form with nothing attached is a user mistake, and 500 tells them to contact someone rather
	 * than to attach a file.
	 */
	@Test
	void anUploadWithNoFileIsAUserErrorNotAServerFault() throws Exception {
		problemDetail(mockMvc.perform(post("/api/market-data/import")
				.with(authAs(seedUser()))
				.cookie(new Cookie("sos_csrf", CSRF)).header("X-CSRF-Token", CSRF)), 400)
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("CSV")));
	}

	/** A real multipart request that simply omits the required part takes the same path. */
	@Test
	void aMultipartRequestMissingItsFilePartIsAlsoAUserError() throws Exception {
		problemDetail(mockMvc.perform(multipart("/api/market-data/import")
				.file(new MockMultipartFile("notTheFileField", "x.csv", "text/csv", "a,b\n".getBytes()))
				.with(authAs(seedUser()))
				.cookie(new Cookie("sos_csrf", CSRF)).header("X-CSRF-Token", CSRF)), 400)
				.andExpect(jsonPath("$.detail").isNotEmpty());
	}

	private ResultActions problemDetail(ResultActions actions, int expectedStatus) throws Exception {
		return actions
				.andExpect(status().is(expectedStatus))
				.andExpect(jsonPath("$.status").value(expectedStatus))
				// The whole point: a human sentence the UI can render as-is.
				.andExpect(jsonPath("$.detail").isNotEmpty());
	}

	private UUID seedUser() {
		jdbcTemplate.update("insert into salary_schema.users (id, email, full_name, password_hash, role) "
				+ "values (gen_random_uuid(), 'hr-admin-problem@acme.test', 'HR Admin', '{argon2}stub', 'HR_ADMIN') "
				+ "on conflict (email) do nothing");
		return jdbcTemplate.queryForObject(
				"select id from salary_schema.users where email = 'hr-admin-problem@acme.test'", UUID.class);
	}

	private static RequestPostProcessor authAs(UUID userId) {
		return SecurityMockMvcRequestPostProcessors.authentication(
				new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_HR_ADMIN"))));
	}

}
