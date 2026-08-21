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
 * P1.3: proves V3 migrates cleanly. Also proves the isolation invariant at the schema level
 * (CLAUDE.md §6.6): employee_demographics carries the FK to employees, never the reverse, so no
 * column on employees itself can leak a demographic attribute via a JPA fetch.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class V3EmployeesMigrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void v3MigratesEmployeesAndDemographicsTables() {
		List<String> tables = jdbcTemplate.queryForList(
				"select table_name from information_schema.tables "
						+ "where table_schema = 'salary_schema' "
						+ "and table_name in ('employees', 'employee_demographics') "
						+ "order by table_name",
				String.class);
		assertThat(tables).containsExactly("employee_demographics", "employees");

		Integer v3Runs = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.flyway_schema_history where success = true and version = '3'",
				Integer.class);
		assertThat(v3Runs).isEqualTo(1);
	}

	@Test
	void employeesTableHasNoColumnReferencingDemographics() {
		List<String> employeeColumns = jdbcTemplate.queryForList(
				"select column_name from information_schema.columns "
						+ "where table_schema = 'salary_schema' and table_name = 'employees'",
				String.class);
		assertThat(employeeColumns).noneMatch(name -> name.toLowerCase().contains("demograph"));

		// The only FK between the two tables runs demographics -> employees.
		Integer fksFromEmployeesToDemographics = jdbcTemplate.queryForObject(
				"select count(*) from information_schema.table_constraints tc "
						+ "join information_schema.constraint_column_usage ccu "
						+ "  on tc.constraint_name = ccu.constraint_name and tc.table_schema = ccu.table_schema "
						+ "where tc.constraint_type = 'FOREIGN KEY' "
						+ "  and tc.table_schema = 'salary_schema' and tc.table_name = 'employees' "
						+ "  and ccu.table_name = 'employee_demographics'",
				Integer.class);
		assertThat(fksFromEmployeesToDemographics).isZero();
	}

	@Test
	void demographicsRowRequiresAnExistingEmployee() {
		UUID randomId = UUID.randomUUID();
		assertThatThrownBy(() -> jdbcTemplate.update(
				"insert into salary_schema.employee_demographics (employee_id, gender) values (?, 'X')",
				randomId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
