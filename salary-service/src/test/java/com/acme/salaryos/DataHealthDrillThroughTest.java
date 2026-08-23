package com.acme.salaryos;

import com.acme.salaryos.analytics.dto.DataHealthCheck;
import com.acme.salaryos.analytics.dto.DataHealthResponse;
import com.acme.salaryos.analytics.service.AnalyticsService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P11.2's Verify: every check on the data-health console drills through to a real row list, and the
 * number of rows it drills to is the number the console reported.
 *
 * <p>This is the assertion that matters for the whole feature. A console that says "357 people
 * report to a terminated manager" and a drill-through that shows 340 of them does not have a
 * display bug — it has two different definitions of the same defect, and a user who notices stops
 * believing both numbers. The count and the predicate live in two places by necessity (one is a
 * {@code count(*)}, the other a Criteria specification on the audited list endpoint), so this
 * reconciliation is the only thing keeping them honest.
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
class DataHealthDrillThroughTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private AnalyticsService analyticsService;

	@Test
	void everyCheckDrillsThroughToExactlyTheRowsItCounted() throws Exception {
		UUID actor = seed();
		DataHealthResponse health = analyticsService.dataHealth();

		assertThat(health.checks()).as("the console must have checks to reconcile").isNotEmpty();

		for (DataHealthCheck check : health.checks()) {
			long drilled = totalCount(actor, drillThroughQuery(check));
			assertThat(drilled)
					.as("check '%s' reported %d but its drill-through (%s) found %d",
							check.key(), check.count(), drillThroughQuery(check), drilled)
					.isEqualTo(check.count());
		}
	}

	/**
	 * The query string the UI actually builds — mirrors {@code dataHealthDrillThroughUrl} in
	 * `analytics.ts`: a check's own {@code filter} when it has one, otherwise {@code
	 * dataHealthCheck=<key>}.
	 *
	 * <p><b>Testing only the {@code dataHealthCheck=} form is what let a real bug through.</b>
	 * {@code terminatedWithOpenPay} carried {@code filter = "status=TERMINATED"} from P11.1, which
	 * matched every terminated employee (420 on the local seed) while the check counted only those
	 * with an open ledger period (0). Reconciling against the parameter the UI does not use proved
	 * the parameter the UI does not use was correct. A {@code filter} is a claim that a list filter
	 * is *equivalent to* the check, and this is what holds that claim to account.
	 */
	private static String drillThroughQuery(DataHealthCheck check) {
		return check.filter() != null ? check.filter() : "dataHealthCheck=" + check.key();
	}

	/**
	 * The seeded defects have to be found, not merely agree at zero. Two checks that both return
	 * nothing reconcile perfectly and prove nothing, so this asserts the fixture's own anomalies
	 * really are visible through the drill-through.
	 */
	@Test
	void theDrillThroughFindsTheSeededAnomalies() throws Exception {
		UUID actor = seed();

		assertThat(totalCount(actor, "dataHealthCheck=terminatedManager"))
				.as("one employee reports to a terminated manager").isGreaterThanOrEqualTo(1);
		assertThat(totalCount(actor, "dataHealthCheck=fullTimePartialFte"))
				.as("one full-time employee is below 1.0 FTE").isGreaterThanOrEqualTo(1);
		assertThat(totalCount(actor, "dataHealthCheck=noCompensation"))
				.as("active employees with no pay record").isGreaterThanOrEqualTo(1);
	}

	/** An unknown key must narrow nothing rather than 500 or return an arbitrary subset. */
	@Test
	void anUnknownCheckKeyNarrowsNothing() throws Exception {
		UUID actor = seed();
		long unfiltered = totalCount(actor, "");
		assertThat(totalCount(actor, "dataHealthCheck=notARealCheck")).isEqualTo(unfiltered);
	}

	/**
	 * The drill-through pins the last-name sort. The compa-ratio sort is a hand-rolled native query
	 * that cannot take a Criteria predicate, and silently dropping the filter would show the wrong
	 * people under the right heading.
	 */
	@Test
	void askingForTheCompaRatioSortDoesNotDropTheDrillThrough() throws Exception {
		UUID actor = seed();
		long sorted = totalCount(actor, "dataHealthCheck=terminatedManager&sortBy=compaRatio");
		assertThat(sorted).isEqualTo(totalCount(actor, "dataHealthCheck=terminatedManager"));
	}

	private long totalCount(UUID actor, String queryString) throws Exception {
		String body = mockMvc.perform(get("/api/employees?limit=200&" + queryString).with(authAs(actor)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode page = objectMapper.readTree(body);
		return page.get("totalCount").asLong();
	}

	/** Deliberate anomalies, one per check that can be produced without fighting a constraint. */
	private UUID seed() {
		jdbcTemplate.update("insert into salary_schema.countries (code, name, default_currency) "
				+ "values ('US', 'United States', 'USD') on conflict (code) do nothing");
		jdbcTemplate.update("insert into salary_schema.locations (id, country_code, city, name) "
				+ "select gen_random_uuid(), 'US', 'Boise', 'Boise DH' "
				+ "where not exists (select 1 from salary_schema.locations where name = 'Boise DH')");
		jdbcTemplate.update("insert into salary_schema.departments (id, name, code) "
				+ "values (gen_random_uuid(), 'Engineering', 'ENG-DH') on conflict (code) do nothing");
		jdbcTemplate.update("insert into salary_schema.job_families (id, name, code) "
				+ "values (gen_random_uuid(), 'Engineering', 'FAM-DH') on conflict (code) do nothing");
		UUID familyId = jdbcTemplate.queryForObject(
				"select id from salary_schema.job_families where code = 'FAM-DH'", UUID.class);
		jdbcTemplate.update("insert into salary_schema.job_levels (id, job_family_id, level_code, title, sort_order) "
						+ "values (gen_random_uuid(), ?, 'L3', 'Engineer DH', 3) on conflict (job_family_id, level_code) do nothing",
				familyId);
		jdbcTemplate.update("insert into salary_schema.users (id, email, full_name, password_hash, role) "
				+ "values (gen_random_uuid(), 'hr-admin-dh2@acme.test', 'HR Admin', '{argon2}stub', 'HR_ADMIN') "
				+ "on conflict (email) do nothing");
		UUID actor = jdbcTemplate.queryForObject(
				"select id from salary_schema.users where email = 'hr-admin-dh2@acme.test'", UUID.class);

		Long already = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.employees where employee_number like 'E-DH2-%'", Long.class);
		if (already != null && already > 0) {
			return actor;
		}

		UUID locationId = jdbcTemplate.queryForObject(
				"select id from salary_schema.locations where name = 'Boise DH' order by id limit 1", UUID.class);
		UUID departmentId = jdbcTemplate.queryForObject(
				"select id from salary_schema.departments where code = 'ENG-DH'", UUID.class);
		UUID levelId = jdbcTemplate.queryForObject(
				"select id from salary_schema.job_levels where job_family_id = ? and level_code = 'L3'", UUID.class, familyId);

		// A terminated manager, and someone active still reporting to them.
		UUID manager = hire("E-DH2-MGR", departmentId, locationId, familyId, levelId, "TERMINATED", BigDecimal.ONE, null);
		hire("E-DH2-REPORT", departmentId, locationId, familyId, levelId, "ACTIVE", BigDecimal.ONE, manager);
		// Full-time at 0.5 FTE -- one of the two fields is wrong.
		hire("E-DH2-FTE", departmentId, locationId, familyId, levelId, "ACTIVE", new BigDecimal("0.50"), null);
		// Active with no pay record at all: hired and never given a salary.
		hire("E-DH2-NOPAY", departmentId, locationId, familyId, levelId, "ACTIVE", BigDecimal.ONE, null);

		return actor;
	}

	private UUID hire(String number, UUID departmentId, UUID locationId, UUID familyId, UUID levelId,
			String status, BigDecimal fte, UUID managerId) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("insert into salary_schema.employees "
						+ "(id, employee_number, first_name, last_name, work_email, department_id, location_id, "
						+ " job_family_id, job_level_id, manager_id, hire_date, employment_type, fte, status, termination_date) "
						+ "values (?, ?, 'First', 'Last', ?, ?, ?, ?, ?, ?, date '2021-01-04', 'FULL_TIME', ?, ?, ?)",
				id, number, number.toLowerCase() + "@acme.test", departmentId, locationId, familyId, levelId,
				managerId, fte, status,
				"TERMINATED".equals(status) ? java.sql.Date.valueOf("2024-06-30") : null);
		return id;
	}

	private static RequestPostProcessor authAs(UUID userId) {
		return SecurityMockMvcRequestPostProcessors.authentication(
				new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_HR_ADMIN"))));
	}

}
