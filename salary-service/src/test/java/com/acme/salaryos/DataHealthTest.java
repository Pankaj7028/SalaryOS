package com.acme.salaryos;

import com.acme.salaryos.analytics.dto.DataHealthCheck;
import com.acme.salaryos.analytics.dto.DataHealthResponse;
import com.acme.salaryos.analytics.dto.DataHealthSeverity;
import com.acme.salaryos.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P11.1's Verify clause: each check's count reconciles against its own direct SQL.
 *
 * <p>Every assertion recomputes the check independently rather than asserting a fixed number — the
 * shared Testcontainers container carries rows from every other test class, and several of them
 * deliberately create terminated employees and band-mismatched records.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class DataHealthTest {

	@Autowired
	private AnalyticsService analyticsService;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private int countOf(String key) {
		return analyticsService.dataHealth().checks().stream()
				.filter(check -> check.key().equals(key))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No check named " + key))
				.count();
	}

	private int sql(String query) {
		Integer result = jdbcTemplate.queryForObject(query, Integer.class);
		return result == null ? 0 : result;
	}

	@Test
	void noBandCountMatchesDirectSql() {
		assertThat(countOf("noBand")).isEqualTo(sql(
				"SELECT count(*) FROM salary_schema.employee_current_comp WHERE band_status = 'NO_BAND'"));
	}

	@Test
	void noCompensationCountMatchesDirectSql() {
		assertThat(countOf("noCompensation")).isEqualTo(sql("""
				SELECT count(*) FROM salary_schema.employees e
				 WHERE e.status <> 'TERMINATED'
				   AND NOT EXISTS (SELECT 1 FROM salary_schema.employee_current_comp c WHERE c.employee_id = e.id)
				"""));
	}

	@Test
	void terminatedWithOpenPayCountMatchesDirectSql() {
		assertThat(countOf("terminatedWithOpenPay")).isEqualTo(sql("""
				SELECT count(*) FROM salary_schema.employees e
				  JOIN salary_schema.compensation_records r ON r.employee_id = e.id
				 WHERE e.status = 'TERMINATED' AND r.effective_to IS NULL
				"""));
	}

	@Test
	void currencyMismatchCountMatchesDirectSql() {
		assertThat(countOf("currencyMismatch")).isEqualTo(sql("""
				SELECT count(*) FROM salary_schema.employee_current_comp c
				  JOIN salary_schema.employees e  ON e.id = c.employee_id
				  JOIN salary_schema.locations l  ON l.id = e.location_id
				  JOIN salary_schema.countries co ON co.code = l.country_code
				 WHERE c.currency <> co.default_currency
				"""));
	}

	/**
	 * The recursive check must terminate and return a number even when the data is clean. A cycle
	 * in this table would otherwise hang the whole endpoint rather than report itself.
	 */
	@Test
	void circularManagementTerminatesAndReportsANumber() {
		assertThat(countOf("circularManagement")).isNotNegative();
	}

	/** A console that hides passing checks cannot answer "is this data clean yet". */
	@Test
	void everyCheckIsReportedIncludingThePassingOnes() {
		DataHealthResponse response = analyticsService.dataHealth();

		assertThat(response.checks()).hasSize(9);
		assertThat(response.checks()).extracting(DataHealthCheck::key).doesNotHaveDuplicates();
		assertThat(response.failingChecks())
				.isEqualTo((int) response.checks().stream().filter(c -> c.count() > 0).count());
	}

	/** Severity orders the console, so it has to actually be ordered. */
	@Test
	void checksAreOrderedMostSevereFirst() {
		List<DataHealthSeverity> severities = analyticsService.dataHealth().checks().stream()
				.map(DataHealthCheck::severity)
				.toList();

		assertThat(severities).isSortedAccordingTo(java.util.Comparator.naturalOrder());
	}

	/** Every check needs a human-readable label and explanation — the UI renders them directly. */
	@Test
	void everyCheckExplainsItself() {
		assertThat(analyticsService.dataHealth().checks()).allSatisfy(check -> {
			assertThat(check.label()).isNotBlank();
			assertThat(check.explanation()).isNotBlank();
			assertThat(check.key()).isNotBlank();
		});
	}

}
