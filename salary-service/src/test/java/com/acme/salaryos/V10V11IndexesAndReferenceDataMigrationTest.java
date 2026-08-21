package com.acme.salaryos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.8: proves V10 (indexes) and V11 (static reference rows) migrate cleanly, and that every
 * index named in Technical-Requirements.md §4.3 exists (via {@code pg_indexes}, the SQL
 * equivalent of {@code \di salary_schema.*}).
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class V10V11IndexesAndReferenceDataMigrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void v10AndV11Migrate() {
		Integer v10Runs = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.flyway_schema_history where success = true and version = '10'",
				Integer.class);
		assertThat(v10Runs).isEqualTo(1);
		Integer v11Runs = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.flyway_schema_history where success = true and version = '11'",
				Integer.class);
		assertThat(v11Runs).isEqualTo(1);
	}

	@Test
	void everyIndexFromTechnicalRequirements43Exists() {
		assertIndexOnTableCovers("employees", "department_id", "status");
		assertIndexOnTableCovers("employees", "location_id", "status");
		assertIndexOnTableCovers("employees", "job_level_id"); // from V3, not repeated in V10
		assertIndexOnTableCovers("employees", "last_name", "id");
		assertIndexDefContains("employees", "gin_trgm_ops");

		assertIndexOnTableCovers("compensation_records", "employee_id", "effective_from");
		assertIndexOnTableCovers("employee_current_comp", "band_status");
		assertIndexOnTableCovers("audit_events", "occurred_at");
		assertIndexOnTableCovers("audit_events", "entity_type", "entity_id");
	}

	@Test
	void currenciesAndReasonCodesAreSeeded() {
		Integer currencyCount = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.currencies", Integer.class);
		assertThat(currencyCount).isGreaterThanOrEqualTo(1);
		Boolean usdPresent = jdbcTemplate.queryForObject(
				"select exists(select 1 from salary_schema.currencies where code = 'USD')", Boolean.class);
		assertThat(usdPresent).isTrue();

		List<String> reasonCodes = jdbcTemplate.queryForList(
				"select code from salary_schema.reason_codes order by code", String.class);
		assertThat(reasonCodes).contains(
				"INITIAL", "MERIT", "PROMOTION", "MARKET_ADJUSTMENT",
				"ROLE_CHANGE", "LOCATION_CHANGE", "CORRECTION", "DEMOTION");
	}

	private void assertIndexOnTableCovers(String table, String... columnsInOrder) {
		String needle = "(" + String.join(", ", columnsInOrder);
		List<String> defs = jdbcTemplate.queryForList(
				"select indexdef from pg_indexes where schemaname = 'salary_schema' and tablename = ?",
				String.class, table);
		assertThat(defs)
				.as("an index on %s covering %s", table, String.join(",", columnsInOrder))
				.anyMatch(def -> def.contains(needle));
	}

	private void assertIndexDefContains(String table, String needle) {
		List<String> defs = jdbcTemplate.queryForList(
				"select indexdef from pg_indexes where schemaname = 'salary_schema' and tablename = ?",
				String.class, table);
		assertThat(defs)
				.as("an index on %s containing '%s'", table, needle)
				.anyMatch(def -> def.contains(needle));
	}

}
