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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P10.5's Verify: {@code totalCount} reconciles against direct SQL for three filter combinations,
 * and the page jump lands on the rows the cursor walk would have reached.
 *
 * <p>The population is built so the two sorts genuinely disagree about how many rows exist: 600
 * employees, of whom 540 have an {@code employee_current_comp} row. The compa-ratio sort inner-joins
 * that table, so its honest total is 540 while the last-name sort's is 600. A count taken from the
 * wrong path would be off by exactly 60 here — which is the bug this test exists to catch, because
 * on a screen it looks like a pagination glitch rather than a query one.
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
class EmployeeListCountAndJumpTest {

	private static final int EMPLOYEE_COUNT = 600;
	/** The remainder have no comp row at all — a day-one hire whose pay is not set yet. */
	private static final int WITH_COMP = 540;
	private static final String PREFIX = "E-CNT";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void totalCountMatchesDirectSqlForThreeFilterCombinations() throws Exception {
		Fixture fx = seed();

		// 1. The bare filter that scopes this test's own rows.
		assertCountMatches(fx, "q=" + PREFIX,
				"select count(*) from salary_schema.employees e where e.employee_number like ?", PREFIX + "%");

		// 2. Filter on a column of employees itself.
		assertCountMatches(fx, "q=" + PREFIX + "&status=ACTIVE",
				"select count(*) from salary_schema.employees e "
						+ "where e.employee_number like ? and e.status = 'ACTIVE'", PREFIX + "%");

		// 3. Filter that reaches through to employee_current_comp -- the one whose SQL the two
		// sorts express differently.
		assertCountMatches(fx, "q=" + PREFIX + "&bandStatus=BELOW_MIN",
				"select count(*) from salary_schema.employees e "
						+ "join salary_schema.employee_current_comp ecc on ecc.employee_id = e.id "
						+ "where e.employee_number like ? and ecc.band_status = 'BELOW_MIN'", PREFIX + "%");
	}

	/** The two sorts count different populations, and each must report its own. */
	@Test
	void eachSortReportsTheCountOfItsOwnPopulation() throws Exception {
		Fixture fx = seed();

		long byLastName = totalCount(fx, "q=" + PREFIX);
		long byCompaRatio = totalCount(fx, "q=" + PREFIX + "&sortBy=compaRatio");

		assertThat(byLastName).as("last-name sort lists employees with no pay set yet").isEqualTo(EMPLOYEE_COUNT);
		assertThat(byCompaRatio).as("compa-ratio sort cannot order an employee who has no compa-ratio").isEqualTo(WITH_COMP);
		assertThat(byLastName - byCompaRatio).isEqualTo(EMPLOYEE_COUNT - WITH_COMP);
	}

	@Test
	void pageJumpLandsOnTheRowsTheCursorWalkWouldHaveReached() throws Exception {
		Fixture fx = seed();
		int limit = 25;
		int targetPage = 4; // 0-based -> rows 100..124

		List<String> walked = new ArrayList<>();
		String cursor = null;
		for (int page = 0; page <= targetPage; page++) {
			JsonNode body = list(fx, "q=" + PREFIX + "&limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor));
			walked.clear();
			for (JsonNode item : body.get("items")) {
				walked.add(item.get("id").asString());
			}
			cursor = body.get("nextCursor").asString();
		}

		JsonNode jumped = list(fx, "q=" + PREFIX + "&limit=" + limit + "&offset=" + (targetPage * limit));
		List<String> jumpedIds = new ArrayList<>();
		for (JsonNode item : jumped.get("items")) {
			jumpedIds.add(item.get("id").asString());
		}

		assertThat(jumpedIds).as("page %d reached by jump == page %d reached by walking", targetPage, targetPage)
				.isEqualTo(walked);
		assertThat(jumpedIds).hasSize(limit);
	}

	/** A jumped-to page must hand back a working cursor, or Next stops working after any jump. */
	@Test
	void aJumpedToPageStillContinuesByCursor() throws Exception {
		Fixture fx = seed();
		int limit = 25;

		JsonNode jumped = list(fx, "q=" + PREFIX + "&limit=" + limit + "&offset=100");
		String cursor = jumped.get("nextCursor").asString();
		assertThat(cursor).isNotBlank();

		JsonNode afterJump = list(fx, "q=" + PREFIX + "&limit=" + limit + "&cursor=" + cursor);
		JsonNode walkedPage5 = list(fx, "q=" + PREFIX + "&limit=" + limit + "&offset=125");

		assertThat(ids(afterJump)).isEqualTo(ids(walkedPage5));
	}

	/** The compa-ratio sort's jump has its own SQL (offset on a native query), so it has its own case. */
	@Test
	void pageJumpWorksOnTheCompaRatioSortToo() throws Exception {
		Fixture fx = seed();
		int limit = 25;

		List<String> walked = null;
		String cursor = null;
		for (int page = 0; page <= 3; page++) {
			JsonNode body = list(fx, "q=" + PREFIX + "&sortBy=compaRatio&limit=" + limit
					+ (cursor == null ? "" : "&cursor=" + cursor));
			walked = ids(body);
			cursor = body.get("nextCursor").asString();
		}

		JsonNode jumped = list(fx, "q=" + PREFIX + "&sortBy=compaRatio&limit=" + limit + "&offset=75");
		assertThat(ids(jumped)).isEqualTo(walked);
	}

	/** A cursor is the more precise position: if both arrive, the cursor is what is honoured. */
	@Test
	void aCursorBeatsAnOffsetWhenBothArePresent() throws Exception {
		Fixture fx = seed();
		JsonNode firstPage = list(fx, "q=" + PREFIX + "&limit=25");
		String cursor = firstPage.get("nextCursor").asString();

		JsonNode both = list(fx, "q=" + PREFIX + "&limit=25&offset=400&cursor=" + cursor);
		JsonNode cursorOnly = list(fx, "q=" + PREFIX + "&limit=25&cursor=" + cursor);

		assertThat(ids(both)).isEqualTo(ids(cursorOnly));
	}

	// --- helpers ---------------------------------------------------------------------------

	private void assertCountMatches(Fixture fx, String queryString, String countSql, Object... args) throws Exception {
		long fromApi = totalCount(fx, queryString);
		long fromSql = jdbcTemplate.queryForObject(countSql, Long.class, args);
		assertThat(fromApi).as("totalCount for `%s`", queryString).isEqualTo(fromSql);
	}

	private long totalCount(Fixture fx, String queryString) throws Exception {
		return list(fx, queryString).get("totalCount").asLong();
	}

	private JsonNode list(Fixture fx, String queryString) throws Exception {
		String body = mockMvc.perform(get("/api/employees?" + queryString).with(authAs(fx.userId())))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body);
	}

	private static List<String> ids(JsonNode page) {
		List<String> ids = new ArrayList<>();
		for (JsonNode item : page.get("items")) {
			ids.add(item.get("id").asString());
		}
		return ids;
	}

	private record Fixture(UUID userId) {
	}

	private Fixture seed() {
		jdbcTemplate.update("insert into salary_schema.countries (code, name, default_currency) "
				+ "values ('US', 'United States', 'USD') on conflict (code) do nothing");
		jdbcTemplate.update("insert into salary_schema.locations (id, country_code, city, name) "
				+ "select gen_random_uuid(), 'US', 'Denver', 'Denver Count' "
				+ "where not exists (select 1 from salary_schema.locations where name = 'Denver Count')");
		jdbcTemplate.update("insert into salary_schema.departments (id, name, code) "
				+ "values (gen_random_uuid(), 'Engineering', 'ENG-CNT') on conflict (code) do nothing");
		jdbcTemplate.update("insert into salary_schema.job_families (id, name, code) "
				+ "values (gen_random_uuid(), 'Engineering', 'ENG-FAM-CNT') on conflict (code) do nothing");
		UUID jobFamilyId = jdbcTemplate.queryForObject(
				"select id from salary_schema.job_families where code = 'ENG-FAM-CNT'", UUID.class);
		jdbcTemplate.update("insert into salary_schema.job_levels (id, job_family_id, level_code, title, sort_order) "
						+ "values (gen_random_uuid(), ?, 'L5', 'Staff Engineer', 5) on conflict (job_family_id, level_code) do nothing",
				jobFamilyId);

		UUID locationId = jdbcTemplate.queryForObject(
				"select id from salary_schema.locations where name = 'Denver Count' order by id limit 1", UUID.class);
		UUID departmentId = jdbcTemplate.queryForObject(
				"select id from salary_schema.departments where code = 'ENG-CNT'", UUID.class);
		UUID jobLevelId = jdbcTemplate.queryForObject(
				"select id from salary_schema.job_levels where job_family_id = ? and level_code = 'L5'", UUID.class, jobFamilyId);

		jdbcTemplate.update("insert into salary_schema.users (id, email, full_name, password_hash, role) "
				+ "values (gen_random_uuid(), 'hr-admin-cnt@acme.test', 'HR Admin', '{argon2}stub', 'HR_ADMIN') "
				+ "on conflict (email) do nothing");

		Long already = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.employees where employee_number like ?", Long.class, PREFIX + "%");
		if (already == null || already == 0) {
			List<UUID> ids = new ArrayList<>(EMPLOYEE_COUNT);
			Object[][] employees = new Object[EMPLOYEE_COUNT][];
			String[] surnames = {"Abbot", "Baker", "Carver", "Dunne", "Ellis", "Frost"};
			for (int i = 0; i < EMPLOYEE_COUNT; i++) {
				UUID id = UUID.randomUUID();
				ids.add(id);
				employees[i] = new Object[] {
						id, PREFIX + "-" + i, "First" + i, surnames[i % surnames.length],
						"employee-cnt-" + i + "@acme.test", departmentId, locationId, jobFamilyId, jobLevelId
				};
			}
			jdbcTemplate.batchUpdate(
					"insert into salary_schema.employees "
							+ "(id, employee_number, first_name, last_name, work_email, department_id, location_id, "
							+ " job_family_id, job_level_id, hire_date, employment_type, fte, status) "
							+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, current_date, 'FULL_TIME', 1.00, 'ACTIVE')",
					Arrays.asList(employees));

			// employee_current_comp is a projection of a real ledger row (it FKs to one), so the
			// ledger row has to exist first -- there is no such thing here as "current pay" that
			// no compensation_record ever produced.
			jdbcTemplate.update("insert into salary_schema.fx_rates (id, rate_month, base_currency, quote_currency, rate) "
					+ "values (gen_random_uuid(), date_trunc('month', current_date)::date, 'USD', 'USD', 1.0) "
					+ "on conflict (rate_month, base_currency, quote_currency) do nothing");
			UUID fxRateId = jdbcTemplate.queryForObject(
					"select id from salary_schema.fx_rates where base_currency = 'USD' and quote_currency = 'USD' "
							+ "order by rate_month desc limit 1", UUID.class);

			// Only the first WITH_COMP get pay at all, and their band_status is spread across the
			// four values so the bandStatus filter has something to count.
			String[] bandStatuses = {"IN_BAND", "BELOW_MIN", "ABOVE_MAX", "NO_BAND"};
			Object[][] records = new Object[WITH_COMP][];
			Object[][] comps = new Object[WITH_COMP][];
			for (int i = 0; i < WITH_COMP; i++) {
				UUID recordId = UUID.randomUUID();
				BigDecimal base = new BigDecimal(120_000 + (i * 37) % 60_000);
				BigDecimal compaRatio = new BigDecimal("0.9000").add(new BigDecimal(i % 40).movePointLeft(2));
				records[i] = new Object[] {recordId, ids.get(i), base, "USD", base, base, "USD", fxRateId, compaRatio};
				comps[i] = new Object[] {
						ids.get(i), recordId, base, "USD", base, base, bandStatuses[i % bandStatuses.length], compaRatio
				};
			}
			jdbcTemplate.batchUpdate(
					"insert into salary_schema.compensation_records "
							+ "(id, employee_id, effective_from, base_amount, currency, pay_frequency, "
							+ " annual_base_amount, normalized_annual_base, base_currency, fx_rate_id, compa_ratio, "
							+ " change_reason, created_by) "
							+ "values (?, ?, current_date, ?, ?, 'ANNUAL', ?, ?, ?, ?, ?, 'HIRE', "
							+ " (select id from salary_schema.users where email = 'hr-admin-cnt@acme.test'))",
					Arrays.asList(records));
			jdbcTemplate.batchUpdate(
					"insert into salary_schema.employee_current_comp "
							+ "(employee_id, compensation_record_id, base_amount, currency, annual_base_amount, "
							+ " normalized_annual_base, band_status, compa_ratio) "
							+ "values (?, ?, ?, ?, ?, ?, ?, ?)",
					Arrays.asList(comps));
		}

		UUID userId = jdbcTemplate.queryForObject(
				"select id from salary_schema.users where email = 'hr-admin-cnt@acme.test'", UUID.class);
		return new Fixture(userId);
	}

	private static RequestPostProcessor authAs(UUID userId) {
		return SecurityMockMvcRequestPostProcessors.authentication(
				new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_HR_ADMIN"))));
	}

}
