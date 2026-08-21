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
 * P1.6: proves V7 migrates cleanly and that {@code one_open_change_per_employee} rejects a second
 * non-terminal change for the same employee (CLAUDE.md §8: at most one open change at a time).
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class V7CompensationChangesMigrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void v7MigratesCompensationChangesTable() {
		List<String> tables = jdbcTemplate.queryForList(
				"select table_name from information_schema.tables "
						+ "where table_schema = 'salary_schema' and table_name = 'compensation_changes'",
				String.class);
		assertThat(tables).containsExactly("compensation_changes");

		Integer v7Runs = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.flyway_schema_history where success = true and version = '7'",
				Integer.class);
		assertThat(v7Runs).isEqualTo(1);
	}

	@Test
	void secondPendingChangeForSameEmployeeIsRejected() {
		UUID employeeId = seedOneEmployee();
		UUID proposerId = seedProposerUser();

		jdbcTemplate.update(insertPendingChangeSql(), employeeId, proposerId);

		assertThatThrownBy(() -> jdbcTemplate.update(insertPendingChangeSql(), employeeId, proposerId))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("one_open_change_per_employee");

		Integer openChanges = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.compensation_changes "
						+ "where employee_id = ? and status in ('DRAFT','PENDING','APPROVED')",
				Integer.class, employeeId);
		assertThat(openChanges).isEqualTo(1);
	}

	private String insertPendingChangeSql() {
		return "insert into salary_schema.compensation_changes "
				+ "(employee_id, status, effective_date, current_base_amount, new_base_amount, currency, "
				+ " change_reason, proposed_by) "
				+ "values (?, 'PENDING', current_date + 30, 100000, 110000, 'USD', 'MERIT', ?)";
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
				+ "values (?, 'E-0001-CHANGE', 'Grace', 'Hopper', 'grace.hopper-change@acme.test', ?, ?, ?, ?, current_date, 'FULL_TIME', 1.00)",
				employeeId, departmentId, locationId, jobFamilyId, jobLevelId);
		return employeeId;
	}

	private UUID seedProposerUser() {
		jdbcTemplate.update("insert into salary_schema.users (id, email, full_name, password_hash, role) "
				+ "values (gen_random_uuid(), 'hr-manager-change@acme.test', 'HR Manager', '{argon2}stub', 'HR_MANAGER') "
				+ "on conflict (email) do nothing");
		return jdbcTemplate.queryForObject(
				"select id from salary_schema.users where email = 'hr-manager-change@acme.test'", UUID.class);
	}

}
