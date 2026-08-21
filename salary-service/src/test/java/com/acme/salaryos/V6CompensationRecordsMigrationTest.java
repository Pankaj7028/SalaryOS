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
 * P1.5 — the highest-value migration test in the build. V6's {@code comp_no_overlap} EXCLUDE
 * constraint is the database-level backstop for "compensation is insert-only, never overlapping"
 * (CLAUDE.md §6.3). Proves it fires for two overlapping periods on the same employee, and that the
 * failure names the constraint rather than surfacing as a generic error.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class V6CompensationRecordsMigrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void v6MigratesCompensationTables() {
		List<String> tables = jdbcTemplate.queryForList(
				"select table_name from information_schema.tables "
						+ "where table_schema = 'salary_schema' "
						+ "and table_name in ('compensation_records', 'compensation_components') "
						+ "order by table_name",
				String.class);
		assertThat(tables).containsExactly("compensation_components", "compensation_records");

		Integer v6Runs = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.flyway_schema_history where success = true and version = '6'",
				Integer.class);
		assertThat(v6Runs).isEqualTo(1);
	}

	@Test
	void overlappingPeriodsForOneEmployeeFailAtTheDatabase() {
		UUID employeeId = seedOneEmployee();
		UUID fxRateId = seedIdentityFxRate();
		UUID userId = seedHrAdminUser();

		jdbcTemplate.update(insertCompRecordSql(),
				employeeId, java.sql.Date.valueOf("2024-01-01"), fxRateId, userId);

		// Overlaps: the first record is open-ended from 2024-01-01, so any later start date
		// intersects it.
		assertThatThrownBy(() -> jdbcTemplate.update(insertCompRecordSql(),
				employeeId, java.sql.Date.valueOf("2024-06-01"), fxRateId, userId))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("comp_no_overlap");

		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.compensation_records where employee_id = ?",
				Integer.class, employeeId);
		assertThat(count).isEqualTo(1);
	}

	private String insertCompRecordSql() {
		return "insert into salary_schema.compensation_records "
				+ "(employee_id, effective_from, base_amount, currency, pay_frequency, "
				+ " annual_base_amount, normalized_annual_base, base_currency, fx_rate_id, change_reason, created_by) "
				+ "values (?, ?, 120000, 'USD', 'ANNUAL', 120000, 120000, 'USD', ?, 'INITIAL', ?)";
	}

	private UUID seedOneEmployee() {
		jdbcTemplate.update("insert into salary_schema.countries (code, name, default_currency) "
				+ "values ('US', 'United States', 'USD') on conflict (code) do nothing");
		jdbcTemplate.update("insert into salary_schema.locations (id, country_code, city, name) "
				+ "values (gen_random_uuid(), 'US', 'Austin', 'Austin HQ') on conflict do nothing");
		jdbcTemplate.update("insert into salary_schema.departments (id, name, code) "
				+ "values (gen_random_uuid(), 'Engineering', 'ENG-DEPT') on conflict (code) do nothing");
		jdbcTemplate.update("insert into salary_schema.job_families (id, name, code) "
				+ "values (gen_random_uuid(), 'Engineering', 'ENG') on conflict (code) do nothing");
		UUID jobFamilyId = jdbcTemplate.queryForObject(
				"select id from salary_schema.job_families where code = 'ENG'", UUID.class);
		jdbcTemplate.update("insert into salary_schema.job_levels (id, job_family_id, level_code, title, sort_order) "
				+ "values (gen_random_uuid(), ?, 'L4', 'Senior Engineer', 4) on conflict (job_family_id, level_code) do nothing",
				jobFamilyId);

		// locations has no unique constraint to key an upsert on; tolerate re-seeding by taking any
		// matching row rather than assuming exactly one.
		UUID locationId = jdbcTemplate.queryForObject(
				"select id from salary_schema.locations where name = 'Austin HQ' order by id limit 1", UUID.class);
		UUID departmentId = jdbcTemplate.queryForObject(
				"select id from salary_schema.departments where code = 'ENG-DEPT'", UUID.class);
		UUID jobLevelId = jdbcTemplate.queryForObject(
				"select id from salary_schema.job_levels where job_family_id = ? and level_code = 'L4'", UUID.class, jobFamilyId);

		UUID employeeId = UUID.randomUUID();
		jdbcTemplate.update("insert into salary_schema.employees "
				+ "(id, employee_number, first_name, last_name, work_email, department_id, location_id, "
				+ " job_family_id, job_level_id, hire_date, employment_type, fte) "
				+ "values (?, 'E-0001-COMP', 'Ada', 'Lovelace', 'ada.lovelace-comp@acme.test', ?, ?, ?, ?, current_date, 'FULL_TIME', 1.00)",
				employeeId, departmentId, locationId, jobFamilyId, jobLevelId);
		return employeeId;
	}

	private UUID seedIdentityFxRate() {
		jdbcTemplate.update("insert into salary_schema.fx_rates (id, rate_month, base_currency, quote_currency, rate) "
				+ "values (gen_random_uuid(), date_trunc('month', current_date)::date, 'USD', 'USD', 1.0) "
				+ "on conflict (rate_month, base_currency, quote_currency) do nothing");
		return jdbcTemplate.queryForObject(
				"select id from salary_schema.fx_rates where base_currency = 'USD' and quote_currency = 'USD' "
						+ "and rate_month = date_trunc('month', current_date)::date",
				UUID.class);
	}

	private UUID seedHrAdminUser() {
		jdbcTemplate.update("insert into salary_schema.users (id, email, full_name, password_hash, role) "
				+ "values (gen_random_uuid(), 'hr-admin-comp@acme.test', 'HR Admin', '{argon2}stub', 'HR_ADMIN') "
				+ "on conflict (email) do nothing");
		return jdbcTemplate.queryForObject(
				"select id from salary_schema.users where email = 'hr-admin-comp@acme.test'", UUID.class);
	}

}
