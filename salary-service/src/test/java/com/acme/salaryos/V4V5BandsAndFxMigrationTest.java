package com.acme.salaryos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.4: proves V4 (salary_bands) and V5 (fx_rates) migrate cleanly, and — the step's specific
 * Verify — that the band ordering check constraint rejects min_amount > mid_amount.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class V4V5BandsAndFxMigrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void v4AndV5MigrateBandsAndFxTables() {
		List<String> tables = jdbcTemplate.queryForList(
				"select table_name from information_schema.tables "
						+ "where table_schema = 'salary_schema' and table_name in ('salary_bands', 'fx_rates') "
						+ "order by table_name",
				String.class);
		assertThat(tables).containsExactly("fx_rates", "salary_bands");

		Integer v4Runs = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.flyway_schema_history where success = true and version = '4'",
				Integer.class);
		assertThat(v4Runs).isEqualTo(1);
		Integer v5Runs = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.flyway_schema_history where success = true and version = '5'",
				Integer.class);
		assertThat(v5Runs).isEqualTo(1);
	}

	@Test
	void bandCheckConstraintRejectsMinGreaterThanMid() {
		// Test classes sharing an identical @SpringBootTest config share a cached context/container
		// (see the FlywayMigrationTest note in BuildPlan.md P1.2), so seed rows idempotently rather
		// than assuming a clean database.
		jdbcTemplate.update("insert into salary_schema.countries (code, name, default_currency) "
				+ "values ('US', 'United States', 'USD') on conflict (code) do nothing");
		jdbcTemplate.update("insert into salary_schema.job_families (id, name, code) "
				+ "values (gen_random_uuid(), 'Engineering', 'ENG') on conflict (code) do nothing");
		UUID jobFamilyId = jdbcTemplate.queryForObject("select id from salary_schema.job_families where code = 'ENG'", UUID.class);
		jdbcTemplate.update("insert into salary_schema.job_levels (id, job_family_id, level_code, title, sort_order) "
				+ "values (gen_random_uuid(), ?, 'L4', 'Senior Engineer', 4) on conflict (job_family_id, level_code) do nothing",
				jobFamilyId);
		UUID jobLevelId = jdbcTemplate.queryForObject(
				"select id from salary_schema.job_levels where job_family_id = ? and level_code = 'L4'", UUID.class, jobFamilyId);
		jdbcTemplate.update("insert into salary_schema.users (id, email, full_name, password_hash, role) "
				+ "values (gen_random_uuid(), 'hr-admin@acme.test', 'HR Admin', '{argon2}stub', 'HR_ADMIN') "
				+ "on conflict (email) do nothing");
		UUID userId = jdbcTemplate.queryForObject("select id from salary_schema.users where email = 'hr-admin@acme.test'", UUID.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"insert into salary_schema.salary_bands "
						+ "(job_level_id, country_code, currency, min_amount, mid_amount, max_amount, effective_from, created_by) "
						+ "values (?, 'US', 'USD', 200000, 150000, 250000, current_date, ?)",
				jobLevelId, userId))
				.isInstanceOf(DataIntegrityViolationException.class);

		jdbcTemplate.update(
				"insert into salary_schema.salary_bands "
						+ "(job_level_id, country_code, currency, min_amount, mid_amount, max_amount, effective_from, created_by) "
						+ "values (?, 'US', 'USD', 100000, 150000, 200000, current_date, ?)",
				jobLevelId, userId);
		Integer count = jdbcTemplate.queryForObject("select count(*) from salary_schema.salary_bands", Integer.class);
		assertThat(count).isEqualTo(1);
	}

}
