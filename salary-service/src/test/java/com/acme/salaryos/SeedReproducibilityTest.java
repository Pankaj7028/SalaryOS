package com.acme.salaryos;

import com.acme.salaryos.seed.Seeder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P9.2: two seed runs against an empty database, from the same {@code randomSeed}, must be
 * identical — totals, medians, and the deliberate-anomaly counts alike. This is what makes a
 * screenshot in a document stay correct and lets the rest of P9's acceptance walkthrough (P9.6)
 * cite fixed numbers.
 *
 * <p>Drives {@link Seeder} directly rather than the {@code seed} profile / {@code
 * SeedRunner}/{@code CommandLineRunner} path — no profile activation needed, and the test
 * controls truncation between runs itself instead of relying on the "refuse if non-empty" guard.
 *
 * <p>{@code @AfterEach} leaves the database empty again: Spring's test context cache reuses the
 * same container/context across test classes with an identical {@code @SpringBootTest}
 * configuration (see {@code FlywayMigrationTest}'s own note on this), so 10,000 leftover
 * employees here collided with unrelated tests' own fixtures the first time this ran without it.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class SeedReproducibilityTest {

	private static final long SEED = 20260820L;
	private static final String BASE_CURRENCY = "USD";

	@Autowired
	private Seeder seeder;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		truncateAll();
	}

	@Test
	void seedingTwiceFromEmptyProducesIdenticalTotalsMediansAndAnomalies() {
		truncateAll();
		Seeder.SeedSummary first = seeder.seedAll(SEED, BASE_CURRENCY);
		Totals firstTotals = readTotals();

		truncateAll();
		Seeder.SeedSummary second = seeder.seedAll(SEED, BASE_CURRENCY);
		Totals secondTotals = readTotals();

		// Every count the generators themselves report -- reference data, employees, and the
		// full Anomalies record (belowMin/aboveMax/noBand/compaRatio range) -- must match exactly.
		assertThat(second.countries()).isEqualTo(first.countries());
		assertThat(second.locations()).isEqualTo(first.locations());
		assertThat(second.departments()).isEqualTo(first.departments());
		assertThat(second.jobFamilies()).isEqualTo(first.jobFamilies());
		assertThat(second.jobLevels()).isEqualTo(first.jobLevels());
		assertThat(second.bandRows()).isEqualTo(first.bandRows());
		assertThat(second.fxRates()).isEqualTo(first.fxRates());
		assertThat(second.users()).isEqualTo(first.users());
		assertThat(second.employees()).isEqualTo(first.employees());
		assertThat(second.anomalies()).isEqualTo(first.anomalies());

		// Independently, the database's own totals and median compensation figure -- not just
		// what the generators claim to have inserted.
		assertThat(secondTotals.employees).isEqualTo(firstTotals.employees);
		assertThat(secondTotals.compensationRecords).isEqualTo(firstTotals.compensationRecords);
		assertThat(secondTotals.compensationComponents).isEqualTo(firstTotals.compensationComponents);
		assertThat(secondTotals.changes).isEqualTo(firstTotals.changes);
		assertThat(secondTotals.currentComp).isEqualTo(firstTotals.currentComp);
		assertThat(secondTotals.medianNormalizedAnnualBase).isEqualByComparingTo(firstTotals.medianNormalizedAnnualBase);
		assertThat(secondTotals.medianCompaRatio).isEqualByComparingTo(firstTotals.medianCompaRatio);
	}

	private record Totals(
			int employees, int compensationRecords, int compensationComponents, int changes, int currentComp,
			BigDecimal medianNormalizedAnnualBase, BigDecimal medianCompaRatio) {
	}

	private Totals readTotals() {
		return new Totals(
				count("salary_schema.employees"),
				count("salary_schema.compensation_records"),
				count("salary_schema.compensation_components"),
				count("salary_schema.compensation_changes"),
				count("salary_schema.employee_current_comp"),
				jdbc.queryForObject(
						"select percentile_cont(0.5) within group (order by normalized_annual_base) "
								+ "from salary_schema.compensation_records", BigDecimal.class),
				jdbc.queryForObject(
						"select percentile_cont(0.5) within group (order by compa_ratio) "
								+ "from salary_schema.compensation_records where compa_ratio is not null", BigDecimal.class));
	}

	private int count(String table) {
		Integer result = jdbc.queryForObject("select count(*) from " + table, Integer.class);
		return result == null ? 0 : result;
	}

	private void truncateAll() {
		jdbc.execute("""
				truncate table
				  salary_schema.employee_current_comp,
				  salary_schema.compensation_components,
				  salary_schema.compensation_records,
				  salary_schema.compensation_changes,
				  salary_schema.employee_demographics,
				  salary_schema.employees,
				  salary_schema.salary_bands,
				  salary_schema.fx_rates,
				  salary_schema.users,
				  salary_schema.job_levels,
				  salary_schema.job_families,
				  salary_schema.departments,
				  salary_schema.locations,
				  salary_schema.countries
				restart identity cascade
				""");
	}

}
