package com.acme.salaryos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.1: proves V1 migrates cleanly against a real Postgres 17 (schema, the three required
 * extensions, and the three identity/access tables) per docs/salary-management-backend.md §2.2.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class FlywayMigrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void v1MigratesSchemaExtensionsAndIdentityTables() {
		List<String> extensions = jdbcTemplate.queryForList(
				"select extname from pg_extension where extname in ('btree_gist','pg_trgm','citext') order by extname",
				String.class);
		assertThat(extensions).containsExactly("btree_gist", "citext", "pg_trgm");

		List<String> tables = jdbcTemplate.queryForList(
				"select table_name from information_schema.tables "
						+ "where table_schema = 'salary_schema' and table_name != 'flyway_schema_history' "
						+ "order by table_name",
				String.class);
		assertThat(tables).containsExactlyInAnyOrder("users", "user_sessions", "password_reset_tokens");

		// Flyway also records a "<< Flyway Schema Creation >>" pseudo-entry (version = null) when it
		// creates a schema that doesn't exist yet, alongside the real V1 row — filter to V1 itself.
		Integer v1Runs = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.flyway_schema_history where success = true and version = '1'",
				Integer.class);
		assertThat(v1Runs).isEqualTo(1);
	}

}
