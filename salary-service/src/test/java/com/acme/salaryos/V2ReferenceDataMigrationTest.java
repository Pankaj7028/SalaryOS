package com.acme.salaryos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.2: proves V2 migrates cleanly (the five reference tables exist) and that the FKs are real —
 * a location referencing a non-existent country is rejected by the database, not the service.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class V2ReferenceDataMigrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void v2MigratesReferenceTables() {
		List<String> tables = jdbcTemplate.queryForList(
				"select table_name from information_schema.tables "
						+ "where table_schema = 'salary_schema' and table_name in "
						+ "('countries','locations','departments','job_families','job_levels') "
						+ "order by table_name",
				String.class);
		assertThat(tables).containsExactly("countries", "departments", "job_families", "job_levels", "locations");

		Integer v2Runs = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.flyway_schema_history where success = true and version = '2'",
				Integer.class);
		assertThat(v2Runs).isEqualTo(1);
	}

	@Test
	void locationRejectsUnknownCountryCode() {
		jdbcTemplate.update("insert into salary_schema.countries (code, name, default_currency) "
				+ "values ('US', 'United States', 'USD') on conflict (code) do nothing");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"insert into salary_schema.locations (country_code, city, name) values ('ZZ', 'Nowhere', 'Nowhere HQ')"))
				.isInstanceOf(DataIntegrityViolationException.class);

		jdbcTemplate.update("insert into salary_schema.locations (country_code, city, name) "
				+ "values ('US', 'Austin', 'Austin HQ V2 Test')");
		// Other test classes sharing this cached context/container seed their own 'US' locations
		// (see the FlywayMigrationTest note in BuildPlan.md P1.2) — filter by this test's own row
		// rather than asserting an exact count across the whole table.
		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.locations where country_code = 'US' and name = 'Austin HQ V2 Test'",
				Integer.class);
		assertThat(count).isEqualTo(1);
	}

}
