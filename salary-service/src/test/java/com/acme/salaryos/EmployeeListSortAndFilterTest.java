package com.acme.salaryos;

import org.junit.jupiter.api.AfterEach;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P9.6's own acceptance walkthrough found the employee list had neither a {@code bandStatus}
 * filter nor a compa-ratio sort — {@code EmployeeController}/{@code EmployeeService} now have
 * both. This proves both against a real Postgres, the way {@link EmployeeListPaginationTest}
 * proves the base keyset sort: page to the end, no duplicate, no skip.
 *
 * <p>The compa-ratio sort specifically needed two real fixes discovered only by running it: (1)
 * Spring Data's keyset cursor extraction walks {@code currentComp.compaRatio} via bean-property
 * reflection on the fetched entity, and throws {@code NullValueInNestedPathException} the moment
 * any row's {@code currentComp} itself is null (not merely its {@code compaRatio}) — {@code
 * EmployeeSpecifications#hasCurrentComp} restricts the compa-ratio-sorted view to employees who
 * have a comp record at all, which {@code list()} applies automatically whenever {@code
 * sortBy=compaRatio}. (2) Postgres's default null ordering for {@code DESC} is NULLS FIRST, not
 * NULLS LAST, which would otherwise bury every real compa-ratio behind the NO_BAND employees
 * (null compa-ratio, but a real comp record) on page one — {@code COMPA_RATIO_SORT} sets {@code
 * nullsLast()} explicitly.
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
class EmployeeListSortAndFilterTest {

	private static final int EMPLOYEE_COUNT = 600;
	/** Every 20th employee (30 of 600) gets no {@code employee_current_comp} row at all — the
	 * exact case that broke keyset cursor extraction before {@code hasCurrentComp()}. */
	private static final int NO_COMP_EVERY_NTH = 20;
	/** Every 15th employee WITH a comp record (roughly 38 of 570) is NO_BAND — comp record
	 * present, {@code compaRatio} null, the nulls-ordering edge case. */
	private static final int NO_BAND_EVERY_NTH = 15;

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private ObjectMapper objectMapper;

	/** This class's two {@code @Test} methods each seed their own {@code E-SORT-*} employees into
	 * the same cached Testcontainers context/schema (see the {@code FlywayMigrationTest} note in
	 * BuildPlan.md P1.2 on container reuse) -- without cleanup the second test's insert collides
	 * on {@code employee_number} with the first's leftover rows. */
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("delete from salary_schema.employee_current_comp where employee_id in "
				+ "(select id from salary_schema.employees where employee_number like 'E-SORT-%')");
		jdbcTemplate.update("delete from salary_schema.compensation_records where employee_id in "
				+ "(select id from salary_schema.employees where employee_number like 'E-SORT-%')");
		jdbcTemplate.update("delete from salary_schema.employees where employee_number like 'E-SORT-%'");
	}

	@Test
	void bandStatusFilterReturnsExactlyMatchingEmployeesWithNoDuplicate() throws Exception {
		Fixture fixture = seedEmployees();
		UUID actorUserId = seedUser();

		for (String bandStatus : List.of("IN_BAND", "BELOW_MIN", "ABOVE_MAX", "NO_BAND")) {
			Set<UUID> expected = fixture.byBandStatus.getOrDefault(bandStatus, Set.of());
			Set<UUID> actual = pageThrough("/api/employees?limit=97&q=E-SORT&bandStatus=" + bandStatus, actorUserId,
					item -> assertThat(item.get("bandStatus").asString()).isEqualTo(bandStatus));
			assertThat(actual).as("bandStatus=%s", bandStatus).isEqualTo(expected);
		}
	}

	@Test
	void compaRatioSortIsNonIncreasingWithNullsLastAndNoDuplicateOrSkip() throws Exception {
		Fixture fixture = seedEmployees();
		UUID actorUserId = seedUser();

		List<BigDecimal> seenInOrder = new ArrayList<>();
		Set<UUID> seenIds = pageThrough("/api/employees?limit=97&q=E-SORT&sortBy=compaRatio", actorUserId,
				item -> {
					JsonNode cr = item.get("compaRatio");
					seenInOrder.add(cr == null || cr.isNull() ? null : new BigDecimal(cr.asString()));
				});

		// Every employee WITH a comp record must appear -- sortBy=compaRatio excludes only the
		// ones with no comp record at all (hasCurrentComp()), never a NO_BAND one (comp record
		// present, band/compa-ratio null).
		assertThat(seenIds).isEqualTo(fixture.withCurrentComp);

		List<BigDecimal> nonNull = seenInOrder.stream().filter(v -> v != null).toList();
		for (int i = 1; i < nonNull.size(); i++) {
			assertThat(nonNull.get(i - 1)).as("row %d must be >= row %d (DESC)", i - 1, i)
					.isGreaterThanOrEqualTo(nonNull.get(i));
		}
		int firstNullIndex = seenInOrder.indexOf(null);
		if (firstNullIndex != -1) {
			assertThat(seenInOrder.subList(firstNullIndex, seenInOrder.size()))
					.as("every null compa-ratio must trail every real one")
					.allMatch(v -> v == null);
		}
		assertThat(seenInOrder.stream().filter(v -> v == null).count()).isEqualTo(fixture.noBandCount);
	}

	/** Pages a MockMvc-backed endpoint to the end, asserting every item via {@code perItem} and
	 * that no id repeats. */
	private Set<UUID> pageThrough(String baseUrl, UUID actorUserId, java.util.function.Consumer<JsonNode> perItem) throws Exception {
		Set<UUID> seenIds = new HashSet<>();
		String cursor = null;
		int pages = 0;
		do {
			String url = cursor == null ? baseUrl : baseUrl + "&cursor=" + cursor;
			String body = mockMvc.perform(get(url).with(authAs(actorUserId)))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			JsonNode page = objectMapper.readTree(body);
			for (JsonNode item : page.get("items")) {
				UUID id = UUID.fromString(item.get("id").asString());
				assertThat(seenIds.add(id)).as("id %s must not be seen twice", id).isTrue();
				perItem.accept(item);
			}
			JsonNode nextCursorNode = page.get("nextCursor");
			cursor = (nextCursorNode == null || nextCursorNode.isNull()) ? null : nextCursorNode.asString();
			pages++;
			assertThat(pages).as("safety valve against an infinite loop").isLessThan(200);
		}
		while (cursor != null);
		return seenIds;
	}

	private record Fixture(
			java.util.Map<String, Set<UUID>> byBandStatus, Set<UUID> withCurrentComp, long noBandCount) {
	}

	private Fixture seedEmployees() {
		jdbcTemplate.update("insert into salary_schema.countries (code, name, default_currency) "
				+ "values ('US', 'United States', 'USD') on conflict (code) do nothing");
		jdbcTemplate.update("insert into salary_schema.locations (id, country_code, city, name) "
				+ "values (gen_random_uuid(), 'US', 'Austin', 'Austin HQ Sort') on conflict do nothing");
		jdbcTemplate.update("insert into salary_schema.departments (id, name, code) "
				+ "values (gen_random_uuid(), 'Engineering', 'ENG-SORT') on conflict (code) do nothing");
		jdbcTemplate.update("insert into salary_schema.job_families (id, name, code) "
				+ "values (gen_random_uuid(), 'Engineering', 'ENG-FAM-SORT') on conflict (code) do nothing");
		UUID jobFamilyId = jdbcTemplate.queryForObject(
				"select id from salary_schema.job_families where code = 'ENG-FAM-SORT'", UUID.class);
		jdbcTemplate.update("insert into salary_schema.job_levels (id, job_family_id, level_code, title, sort_order) "
						+ "values (gen_random_uuid(), ?, 'L4', 'Senior Engineer', 4) on conflict (job_family_id, level_code) do nothing",
				jobFamilyId);
		UUID locationId = jdbcTemplate.queryForObject(
				"select id from salary_schema.locations where name = 'Austin HQ Sort' order by id limit 1", UUID.class);
		UUID departmentId = jdbcTemplate.queryForObject(
				"select id from salary_schema.departments where code = 'ENG-SORT'", UUID.class);
		UUID jobLevelId = jdbcTemplate.queryForObject(
				"select id from salary_schema.job_levels where job_family_id = ? and level_code = 'L4'", UUID.class, jobFamilyId);
		UUID createdBy = seedUser();
		jdbcTemplate.update("insert into salary_schema.fx_rates (id, rate_month, base_currency, quote_currency, rate) "
				+ "values (gen_random_uuid(), date_trunc('month', current_date), 'USD', 'USD', 1) on conflict do nothing");
		UUID fxRateId = jdbcTemplate.queryForObject(
				"select id from salary_schema.fx_rates where base_currency = 'USD' and quote_currency = 'USD' "
						+ "and rate_month = date_trunc('month', current_date)",
				UUID.class);

		java.util.Map<String, Set<UUID>> byBandStatus = new java.util.HashMap<>();
		Set<UUID> withCurrentComp = new HashSet<>();
		long noBandCount = 0;

		Object[][] employeeRows = new Object[EMPLOYEE_COUNT][];
		List<Object[]> compRows = new ArrayList<>();
		String[] statuses = { "IN_BAND", "BELOW_MIN", "ABOVE_MAX" };

		for (int i = 0; i < EMPLOYEE_COUNT; i++) {
			UUID id = UUID.randomUUID();
			employeeRows[i] = new Object[] {
					id, "E-SORT-" + i, "First" + i, "Last" + i, "employee-sort-" + i + "@acme.test",
					departmentId, locationId, jobFamilyId, jobLevelId
			};

			if (i % NO_COMP_EVERY_NTH == 0) {
				continue; // no employee_current_comp row at all
			}
			withCurrentComp.add(id);

			boolean isNoBand = i % NO_BAND_EVERY_NTH == 0;
			String bandStatus = isNoBand ? "NO_BAND" : statuses[i % statuses.length];
			BigDecimal compaRatio = isNoBand ? null : BigDecimal.valueOf(0.5 + (i % 100) / 100.0).setScale(4, java.math.RoundingMode.HALF_UP);
			if (isNoBand) {
				noBandCount++;
			}
			byBandStatus.computeIfAbsent(bandStatus, k -> new HashSet<>()).add(id);

			compRows.add(new Object[] {
					id, UUID.randomUUID(), BigDecimal.valueOf(100000), "USD",
					BigDecimal.valueOf(100000), BigDecimal.valueOf(100000), null, compaRatio, null, bandStatus });
		}

		jdbcTemplate.batchUpdate(
				"insert into salary_schema.employees "
						+ "(id, employee_number, first_name, last_name, work_email, department_id, location_id, "
						+ " job_family_id, job_level_id, hire_date, employment_type, fte) "
						+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, current_date, 'FULL_TIME', 1.00)",
				List.of(employeeRows));

		// employee_current_comp.compensation_record_id has a NOT NULL FK -- seed one throwaway
		// compensation_records row per comp row rather than relaxing the constraint for the test.
		for (Object[] row : compRows) {
			UUID recordId = (UUID) row[1];
			jdbcTemplate.update(
					"insert into salary_schema.compensation_records "
							+ "(id, employee_id, effective_from, base_amount, currency, pay_frequency, "
							+ " annual_base_amount, normalized_annual_base, base_currency, fx_rate_id, change_reason, created_by) "
							+ "values (?, ?, current_date, 100000, 'USD', 'ANNUAL', 100000, 100000, 'USD', ?, 'INITIAL', ?)",
					recordId, row[0], fxRateId, createdBy);
		}
		jdbcTemplate.batchUpdate(
				"insert into salary_schema.employee_current_comp "
						+ "(employee_id, compensation_record_id, base_amount, currency, annual_base_amount, "
						+ " normalized_annual_base, band_id, compa_ratio, range_penetration, band_status) "
						+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				compRows);

		return new Fixture(byBandStatus, withCurrentComp, noBandCount);
	}

	private UUID seedUser() {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("insert into salary_schema.users (id, email, full_name, password_hash, role) "
						+ "values (?, 'hr-admin-sort@acme.test', 'HR Admin', '{argon2}stub', 'HR_ADMIN') on conflict (email) do nothing",
				id);
		return jdbcTemplate.queryForObject("select id from salary_schema.users where email = 'hr-admin-sort@acme.test'", UUID.class);
	}

	private static RequestPostProcessor authAs(UUID userId) {
		return SecurityMockMvcRequestPostProcessors.authentication(
				new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_HR_ADMIN"))));
	}

}
