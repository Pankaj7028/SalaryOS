package com.acme.salaryos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P4.1's specific Verify: keyset pagination pages to the end of 10k rows with no duplicate or
 * skipped id (acceptance criterion #2). 10,000 employees are batch-inserted directly via JDBC —
 * this is not P9's seed generator (no realistic distributions or deliberate anomalies), just
 * enough rows, with deliberately repeated last names, to prove the (last_name, id) keyset
 * pagination visits every row exactly once.
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
class EmployeeListPaginationTest {

	private static final int EMPLOYEE_COUNT = 10_000;
	private static final String[] SURNAMES = {"Smith", "Garcia", "Chen", "Patel", "Johnson", "Kim", "Nguyen", "Silva"};

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void pagesThroughTenThousandEmployeesWithNoDuplicateOrSkippedId() throws Exception {
		Set<UUID> insertedIds = seedEmployees();
		assertThat(insertedIds).hasSize(EMPLOYEE_COUNT);
		UUID actorUserId = seedUser();

		Set<UUID> seenIds = new HashSet<>();
		int totalItemsSeen = 0;
		String cursor = null;
		int pages = 0;

		// Scoped to this test's own rows via q=E-PAGE: other test classes sharing this cached
		// context/container (see the FlywayMigrationTest note in BuildPlan.md P1.2) may have their
		// own employees in the same schema, and an unfiltered list would pick those up too.
		do {
			String url = cursor == null
					? "/api/employees?limit=137&q=E-PAGE"
					: "/api/employees?limit=137&q=E-PAGE&cursor=" + cursor;
			String body = mockMvc.perform(get(url).with(authAs(actorUserId)))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();

			JsonNode page = objectMapper.readTree(body);
			JsonNode items = page.get("items");
			for (JsonNode item : items) {
				UUID id = UUID.fromString(item.get("id").asString());
				assertThat(seenIds.add(id)).as("id %s must not be seen twice", id).isTrue();
				totalItemsSeen++;
			}

			JsonNode nextCursorNode = page.get("nextCursor");
			cursor = (nextCursorNode == null || nextCursorNode.isNull()) ? null : nextCursorNode.asString();
			pages++;
			assertThat(pages).as("safety valve against an infinite loop").isLessThan(200);
		}
		while (cursor != null);

		assertThat(totalItemsSeen).isEqualTo(EMPLOYEE_COUNT);
		assertThat(seenIds).isEqualTo(insertedIds);
	}

	private Set<UUID> seedEmployees() {
		jdbcTemplate.update("insert into salary_schema.countries (code, name, default_currency) "
				+ "values ('US', 'United States', 'USD') on conflict (code) do nothing");
		jdbcTemplate.update("insert into salary_schema.locations (id, country_code, city, name) "
				+ "values (gen_random_uuid(), 'US', 'Austin', 'Austin HQ Pagination') on conflict do nothing");
		jdbcTemplate.update("insert into salary_schema.departments (id, name, code) "
				+ "values (gen_random_uuid(), 'Engineering', 'ENG-PAGE') on conflict (code) do nothing");
		jdbcTemplate.update("insert into salary_schema.job_families (id, name, code) "
				+ "values (gen_random_uuid(), 'Engineering', 'ENG-FAM-PAGE') on conflict (code) do nothing");
		UUID jobFamilyId = jdbcTemplate.queryForObject(
				"select id from salary_schema.job_families where code = 'ENG-FAM-PAGE'", UUID.class);
		jdbcTemplate.update("insert into salary_schema.job_levels (id, job_family_id, level_code, title, sort_order) "
				+ "values (gen_random_uuid(), ?, 'L4', 'Senior Engineer', 4) on conflict (job_family_id, level_code) do nothing",
				jobFamilyId);

		UUID locationId = jdbcTemplate.queryForObject(
				"select id from salary_schema.locations where name = 'Austin HQ Pagination' order by id limit 1", UUID.class);
		UUID departmentId = jdbcTemplate.queryForObject(
				"select id from salary_schema.departments where code = 'ENG-PAGE'", UUID.class);
		UUID jobLevelId = jdbcTemplate.queryForObject(
				"select id from salary_schema.job_levels where job_family_id = ? and level_code = 'L4'", UUID.class, jobFamilyId);

		Set<UUID> ids = new HashSet<>(EMPLOYEE_COUNT);
		Object[][] batchArgs = new Object[EMPLOYEE_COUNT][];
		for (int i = 0; i < EMPLOYEE_COUNT; i++) {
			UUID id = UUID.randomUUID();
			ids.add(id);
			String surname = SURNAMES[i % SURNAMES.length];
			batchArgs[i] = new Object[] {
					id, "E-PAGE-" + i, "First" + i, surname, "employee-page-" + i + "@acme.test",
					departmentId, locationId, jobFamilyId, jobLevelId
			};
		}
		jdbcTemplate.batchUpdate(
				"insert into salary_schema.employees "
						+ "(id, employee_number, first_name, last_name, work_email, department_id, location_id, "
						+ " job_family_id, job_level_id, hire_date, employment_type, fte) "
						+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, current_date, 'FULL_TIME', 1.00)",
				java.util.Arrays.asList(batchArgs));

		return ids;
	}

	/** The audited {@code list()} read needs a real {@code users} row — {@code AuditService} looks
	 * up the actor's role by id, and this test seeds everything via JDBC rather than repositories. */
	private UUID seedUser() {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("insert into salary_schema.users (id, email, full_name, password_hash, role) "
						+ "values (?, 'hr-admin-page@acme.test', 'HR Admin', '{argon2}stub', 'HR_ADMIN') on conflict (email) do nothing",
				id);
		return jdbcTemplate.queryForObject("select id from salary_schema.users where email = 'hr-admin-page@acme.test'", UUID.class);
	}

	/** Real {@code UsernamePasswordAuthenticationToken(UUID, null, authorities)} shape — matches
	 * {@code SessionCookieAuthFilter} exactly, since {@code @AuthenticationPrincipal UUID} only
	 * binds when the principal really is a {@code UUID} ({@code @WithMockUser}'s principal isn't). */
	private static RequestPostProcessor authAs(UUID userId) {
		return SecurityMockMvcRequestPostProcessors.authentication(
				new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_HR_ADMIN"))));
	}

}
