package com.acme.salaryos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The P0.2 "boots with no datasource" scenario ({@code spring.autoconfigure.exclude} still set in
 * application.yml, deleted wholesale at P0.3) stopped being reachable at P2.1: {@code
 * SecurityConfig} now hard-requires {@code UserSessionRepository} to check revoked sessions on
 * every request, so the context can no longer load without persistence. Needs the same
 * Testcontainers wiring as every other integration test until P0.3 provides real Neon config.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class SalaryOsApplicationTests {

	@Test
	void contextLoads() {
	}

}
