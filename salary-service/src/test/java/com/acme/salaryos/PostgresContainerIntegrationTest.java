package com.acme.salaryos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.5: proves the Testcontainers Postgres 17 base config ({@link TestcontainersConfiguration})
 * actually boots a container and the app can talk to it. The P0.2 {@code spring.autoconfigure.exclude}
 * block (removed at P0.3) is cleared here so the datasource/JPA/Flyway autoconfiguration this test
 * needs is active, without touching the main profile used for the exclusion-free boot check.
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude=")
@Import(TestcontainersConfiguration.class)
class PostgresContainerIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void connectsToRealPostgres17ViaTestcontainers() {
		assertThat(jdbcTemplate.queryForObject("select 1", Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("show server_version", String.class)).startsWith("17");
	}

}
