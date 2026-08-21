package com.acme.salaryos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.7: proves V8 (audit_events, append-only grants) and V9 (employee_current_comp) migrate
 * cleanly, and — the step's specific Verify — that an UPDATE on audit_events, run as the
 * application's own database role (salaryos_app), is denied by the database, not just the
 * service layer (FR-7.3).
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class V8V9AuditAndProjectionMigrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PostgreSQLContainer postgresContainer;

	@Test
	void v8AndV9MigrateAuditAndProjectionTables() {
		List<String> tables = jdbcTemplate.queryForList(
				"select table_name from information_schema.tables "
						+ "where table_schema = 'salary_schema' "
						+ "and table_name in ('audit_events', 'employee_current_comp') "
						+ "order by table_name",
				String.class);
		assertThat(tables).containsExactly("audit_events", "employee_current_comp");

		Integer v8Runs = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.flyway_schema_history where success = true and version = '8'",
				Integer.class);
		assertThat(v8Runs).isEqualTo(1);
		Integer v9Runs = jdbcTemplate.queryForObject(
				"select count(*) from salary_schema.flyway_schema_history where success = true and version = '9'",
				Integer.class);
		assertThat(v9Runs).isEqualTo(1);
	}

	@Test
	void appRoleCanInsertAndSelectButNotUpdateAuditEvents() {
		UUID actorId = seedActorUser();

		JdbcTemplate appJdbcTemplate = new JdbcTemplate(appRoleDataSource());

		// Granted: INSERT and SELECT.
		appJdbcTemplate.update(
				"insert into salary_schema.audit_events (actor_user_id, actor_role, action, entity_type) "
						+ "values (?, 'HR_ADMIN', 'VIEW', 'Employee')",
				actorId);
		Integer count = appJdbcTemplate.queryForObject(
				"select count(*) from salary_schema.audit_events where actor_user_id = ?", Integer.class, actorId);
		assertThat(count).isEqualTo(1);

		// Not granted: UPDATE. This is the step's Verify. Spring classifies Postgres's 42501
		// (insufficient_privilege) as BadSqlGrammarException rather than a permission-specific
		// subtype, so assert on the wrapped PSQLException's message, not the outer one.
		assertThatThrownBy(() -> appJdbcTemplate.update(
				"update salary_schema.audit_events set action = 'TAMPERED' where actor_user_id = ?", actorId))
				.isInstanceOf(DataAccessException.class)
				.rootCause()
				.hasMessageContaining("permission denied");
	}

	private DriverManagerDataSource appRoleDataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl(postgresContainer.getJdbcUrl());
		dataSource.setUsername("salaryos_app");
		// Matches the fallback password V8 creates the role with when it doesn't already exist
		// (Testcontainers/local only — Neon provisions its own credential out-of-band).
		dataSource.setPassword("local-dev-only-not-a-secret");
		return dataSource;
	}

	private UUID seedActorUser() {
		jdbcTemplate.update("insert into salary_schema.users (id, email, full_name, password_hash, role) "
				+ "values (gen_random_uuid(), 'auditor-audit@acme.test', 'Auditor', '{argon2}stub', 'AUDITOR') "
				+ "on conflict (email) do nothing");
		return jdbcTemplate.queryForObject(
				"select id from salary_schema.users where email = 'auditor-audit@acme.test'", UUID.class);
	}

}
