package com.acme.salaryos;

import com.acme.salaryos.market.dto.MarketImportResult;
import com.acme.salaryos.market.dto.MarketImportRowResult;
import com.acme.salaryos.market.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P11.5's Verify clause: a file with deliberate errors imports the good rows and reports the bad
 * ones, with no partial-batch rollback.
 *
 * <p>Uses its own job family, country and level so nothing here depends on — or disturbs — what
 * other classes have left in the shared container. Country code {@code QM}: not {@code ZZ}, which
 * {@code V2ReferenceDataMigrationTest} needs to stay invalid, and not {@code QX}, which
 * {@code BandHealthTest} owns.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class MarketDataImportTest {

	private static final String COUNTRY = "QM";
	private static final String HEADER = "source,jobLevelId,countryCode,currency,p25,p50,p75,effectiveMonth";

	@Autowired
	private MarketDataService marketDataService;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private UUID jobLevelId;
	private UUID importedBy;

	@BeforeEach
	void seedReferenceData() {
		jdbcTemplate.update("INSERT INTO salary_schema.countries (code, name, default_currency) "
				+ "VALUES (?, 'Market Fixture Land', 'USD') ON CONFLICT (code) DO NOTHING", COUNTRY);

		Integer families = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM salary_schema.job_families WHERE code = 'MARKETFIX'", Integer.class);
		if (families == null || families == 0) {
			jdbcTemplate.update("INSERT INTO salary_schema.job_families (name, code) "
					+ "VALUES ('Market Fixture', 'MARKETFIX')");
			String familyId = jdbcTemplate.queryForObject(
					"SELECT id::text FROM salary_schema.job_families WHERE code = 'MARKETFIX'", String.class);
			jdbcTemplate.update("INSERT INTO salary_schema.job_levels "
					+ "(job_family_id, level_code, title, sort_order) VALUES (?::uuid, 'L5', 'Market Fixture L5', 5)",
					familyId);
		}

		jobLevelId = UUID.fromString(jdbcTemplate.queryForObject(
				"SELECT jl.id::text FROM salary_schema.job_levels jl "
						+ "JOIN salary_schema.job_families jf ON jf.id = jl.job_family_id "
						+ "WHERE jf.code = 'MARKETFIX'", String.class));

		importedBy = UUID.fromString(jdbcTemplate.queryForObject(
				"INSERT INTO salary_schema.users (email, full_name, password_hash, role) "
						+ "VALUES (?, 'Market Importer', '{noop}x', 'HR_ADMIN') RETURNING id::text",
				String.class, "market-" + UUID.randomUUID() + "@acme.test"));
	}

	private MockMultipartFile csv(String... rows) {
		String body = HEADER + "\n" + String.join("\n", rows) + "\n";
		return new MockMultipartFile("file", "market.csv", "text/csv", body.getBytes(StandardCharsets.UTF_8));
	}

	private String goodRow(String source, String month, int p50) {
		return "%s,%s,%s,USD,%d,%d,%d,%s".formatted(source, jobLevelId, COUNTRY, p50 - 10000, p50, p50 + 10000, month);
	}

	/** The headline promise: bad rows are reported, good rows still land. */
	@Test
	void badRowsAreReportedAndTheGoodRowsStillImport() {
		String source = "Survey-" + UUID.randomUUID();

		MarketImportResult result = marketDataService.importCsv(csv(
				goodRow(source, "2026-01-01", 100000),
				goodRow(source, "2026-02-01", 105000),
				"%s,not-a-uuid,%s,USD,1,2,3,2026-01-01".formatted(source, COUNTRY),
				"%s,%s,%s,USD,300000,200000,100000,2026-03-01".formatted(source, jobLevelId, COUNTRY),
				goodRow(source, "2026-04-01", 110000)),
				false, importedBy);

		assertThat(result.totalRows()).isEqualTo(5);
		assertThat(result.created()).isEqualTo(3);
		assertThat(result.errors()).isEqualTo(2);
		assertThat(result.rowsApplied()).isEqualTo(3);

		Integer persisted = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM salary_schema.market_data_points WHERE source = ?", Integer.class, source);
		assertThat(persisted).as("no partial-batch rollback — the good rows survive").isEqualTo(3);
	}

	/** Out-of-order percentiles get a readable message, not a constraint-violation stack trace. */
	@Test
	void outOfOrderPercentilesAreRejectedWithAReadableMessage() {
		String source = "Survey-" + UUID.randomUUID();

		MarketImportResult result = marketDataService.importCsv(csv(
				"%s,%s,%s,USD,300000,200000,100000,2026-05-01".formatted(source, jobLevelId, COUNTRY)),
				false, importedBy);

		assertThat(result.rows()).singleElement()
				.extracting(MarketImportRowResult::error).asString()
				.contains("p25 <= p50 <= p75");
	}

	/**
	 * An unknown job level must be an ERROR row, not a transaction abort. Without the pre-insert FK
	 * lookup this poisons the whole transaction in Postgres and every later row fails too.
	 */
	@Test
	void anUnknownJobLevelIsARowErrorAndDoesNotPoisonTheBatch() {
		String source = "Survey-" + UUID.randomUUID();

		MarketImportResult result = marketDataService.importCsv(csv(
				"%s,%s,%s,USD,90000,100000,110000,2026-06-01".formatted(source, UUID.randomUUID(), COUNTRY),
				goodRow(source, "2026-07-01", 120000)),
				false, importedBy);

		assertThat(result.errors()).isEqualTo(1);
		assertThat(result.created()).isEqualTo(1);

		Integer persisted = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM salary_schema.market_data_points WHERE source = ?", Integer.class, source);
		assertThat(persisted).isEqualTo(1);
	}

	/** A dry run diffs without writing — the same contract the bands importer has. */
	@Test
	void aDryRunAppliesNothing() {
		String source = "Survey-" + UUID.randomUUID();

		MarketImportResult result = marketDataService.importCsv(
				csv(goodRow(source, "2026-08-01", 130000)), true, importedBy);

		assertThat(result.dryRun()).isTrue();
		assertThat(result.created()).isEqualTo(1);
		assertThat(result.rowsApplied()).isZero();

		Integer persisted = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM salary_schema.market_data_points WHERE source = ?", Integer.class, source);
		assertThat(persisted).isZero();
	}

	/** Re-importing a corrected survey updates in place — never two contradictory p50s for one cell. */
	@Test
	void reImportingACorrectedSurveyUpdatesRatherThanDuplicating() {
		String source = "Survey-" + UUID.randomUUID();
		marketDataService.importCsv(csv(goodRow(source, "2026-09-01", 100000)), false, importedBy);

		MarketImportResult second = marketDataService.importCsv(
				csv(goodRow(source, "2026-09-01", 140000)), false, importedBy);

		assertThat(second.updated()).isEqualTo(1);
		assertThat(second.created()).isZero();

		Integer rows = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM salary_schema.market_data_points WHERE source = ?", Integer.class, source);
		assertThat(rows).isEqualTo(1);

		assertThat(jdbcTemplate.queryForObject(
				"SELECT p50_amount FROM salary_schema.market_data_points WHERE source = ?",
				java.math.BigDecimal.class, source))
				.isEqualByComparingTo("140000");
	}

	/** Any day in the month normalises to the first, matching how surveys are published. */
	@Test
	void effectiveMonthNormalisesToTheFirstOfTheMonth() {
		String source = "Survey-" + UUID.randomUUID();

		marketDataService.importCsv(csv(goodRow(source, "2026-10-17", 150000)), false, importedBy);

		assertThat(jdbcTemplate.queryForObject(
				"SELECT effective_month::text FROM salary_schema.market_data_points WHERE source = ?",
				String.class, source))
				.isEqualTo("2026-10-01");
	}

}
