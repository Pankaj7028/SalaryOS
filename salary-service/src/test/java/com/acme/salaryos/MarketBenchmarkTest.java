package com.acme.salaryos;

import com.acme.salaryos.analytics.dto.BandHealthRow;
import com.acme.salaryos.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P11.6's Verify, the half that can be asserted without a browser: a band with no market data is
 * unchanged and carries no empty tick, a band with data carries the right figure, and a survey in
 * the wrong currency is dropped rather than drawn.
 *
 * <p>The last one is the point of the whole feature's caution. A market median is a number people
 * make pay decisions against, and a GBP band ticked with a USD median would read as roughly 25%
 * under market — a plausible-looking figure, in the right shape, wrong. Nothing about it would look
 * broken.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class MarketBenchmarkTest {

	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private AnalyticsService analyticsService;

	/** A band nobody has imported a survey for — the ordinary case, and it must look untouched. */
	@Test
	void aBandWithNoMarketDataCarriesNoBenchmarkAtAll() {
		Fixture fx = seed("NONE");

		BandHealthRow band = bandFor(fx.bandId());
		assertThat(band.marketP50()).isNull();
		assertThat(band.midVsMarketP50()).isNull();
	}

	@Test
	void aBandWithMarketDataCarriesTheMedianAndHowFarItsMidSitsFromIt() {
		Fixture fx = seed("HAS");
		// Band mid is 120,000; market median 100,000 -> mid sits 20% above market.
		importPoint(fx, "SurveyCo", "100000.00", "USD", "2026-01-01");

		BandHealthRow band = bandFor(fx.bandId());
		assertThat(band.marketP50()).isNotNull();
		assertThat(band.marketP50().amount()).isEqualByComparingTo("100000.00");
		assertThat(band.marketP50().currency()).isEqualTo("USD");
		assertThat(band.midVsMarketP50()).isEqualByComparingTo("0.2000");
	}

	/**
	 * A survey denominated differently from the band is dropped, not converted. Converting would
	 * pin the benchmark to one month's FX rate and make it drift for reasons unrelated to the
	 * market; drawing it as-is would be a silent lie on a scale the reader trusts to be one
	 * currency (CLAUDE.md §6.2).
	 */
	@Test
	void aSurveyInAnotherCurrencyIsDroppedRatherThanConverted() {
		Fixture fx = seed("CCY");
		importPoint(fx, "SurveyCo", "100000.00", "EUR", "2026-01-01");

		BandHealthRow band = bandFor(fx.bandId());
		assertThat(band.marketP50()).as("a EUR survey must not appear on a USD band").isNull();
		assertThat(band.midVsMarketP50()).isNull();
	}

	/** Two surveys, and the answer must not depend on which row the database returns first. */
	@Test
	void theNewestMonthWinsAndTiesBreakDeterministically() {
		Fixture fx = seed("MANY");
		importPoint(fx, "OldSurvey", "80000.00", "USD", "2025-01-01");
		importPoint(fx, "NewSurvey", "110000.00", "USD", "2026-06-01");

		assertThat(bandFor(fx.bandId()).marketP50().amount()).isEqualByComparingTo("110000.00");

		// Same month, two sources: source ascending breaks the tie, so two runs agree.
		importPoint(fx, "AaaSurvey", "105000.00", "USD", "2026-06-01");
		assertThat(bandFor(fx.bandId()).marketP50().amount()).isEqualByComparingTo("105000.00");
		assertThat(bandFor(fx.bandId()).marketP50().amount()).isEqualByComparingTo("105000.00");
	}

	// --- helpers ---------------------------------------------------------------------------

	private BandHealthRow bandFor(UUID bandId) {
		return analyticsService.bandHealth().rows().stream()
				.filter(row -> row.bandId().equals(bandId))
				.findFirst()
				.orElseThrow(() -> new AssertionError("band " + bandId + " is not in band health"));
	}

	private void importPoint(Fixture fx, String source, String p50, String currency, String month) {
		jdbcTemplate.update("insert into salary_schema.market_data_points "
						+ "(id, source, job_level_id, country_code, currency, p25_amount, p50_amount, p75_amount, "
						+ " effective_month, imported_by) "
						+ "values (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, cast(? as date), ?) "
						+ "on conflict (source, job_level_id, country_code, effective_month) do nothing",
				source, fx.jobLevelId(), fx.countryCode(), currency,
				new BigDecimal(p50).multiply(new BigDecimal("0.85")),
				new BigDecimal(p50),
				new BigDecimal(p50).multiply(new BigDecimal("1.15")),
				java.time.LocalDate.parse(month), fx.userId());
	}

	private record Fixture(UUID bandId, UUID jobLevelId, String countryCode, UUID userId) {
	}

	/** Its own country per tag: band health is keyed on (job level, country), so sharing either
	 * across cases would let one case's survey appear in another's band. */
	private Fixture seed(String tag) {
		String country = switch (tag) {
			case "NONE" -> "MK";
			case "HAS" -> "MT";
			case "CCY" -> "LV";
			default -> "LT";
		};
		jdbcTemplate.update("insert into salary_schema.countries (code, name, default_currency) "
				+ "values (?, ?, 'USD') on conflict (code) do nothing", country, "Market " + tag);
		jdbcTemplate.update("insert into salary_schema.job_families (id, name, code) "
				+ "values (gen_random_uuid(), 'Market Fam', ?) on conflict (code) do nothing", "FAM-MKT-" + tag);
		UUID familyId = jdbcTemplate.queryForObject(
				"select id from salary_schema.job_families where code = ?", UUID.class, "FAM-MKT-" + tag);
		jdbcTemplate.update("insert into salary_schema.job_levels (id, job_family_id, level_code, title, sort_order) "
						+ "values (gen_random_uuid(), ?, 'L5', ?, 5) on conflict (job_family_id, level_code) do nothing",
				familyId, "Market Level " + tag);
		UUID jobLevelId = jdbcTemplate.queryForObject(
				"select id from salary_schema.job_levels where job_family_id = ? and level_code = 'L5'",
				UUID.class, familyId);
		jdbcTemplate.update("insert into salary_schema.users (id, email, full_name, password_hash, role) "
						+ "values (gen_random_uuid(), ?, 'HR Admin', '{argon2}stub', 'HR_ADMIN') "
						+ "on conflict (email) do nothing",
				"hr-mkt-" + tag.toLowerCase() + "@acme.test");
		UUID userId = jdbcTemplate.queryForObject("select id from salary_schema.users where email = ?",
				UUID.class, "hr-mkt-" + tag.toLowerCase() + "@acme.test");

		jdbcTemplate.update("insert into salary_schema.salary_bands "
						+ "(id, job_level_id, country_code, currency, min_amount, mid_amount, max_amount, "
						+ " effective_from, created_by) "
						+ "select gen_random_uuid(), ?, ?, 'USD', 100000.00, 120000.00, 140000.00, "
						+ "       date '2025-01-01', ? "
						+ " where not exists (select 1 from salary_schema.salary_bands "
						+ "                    where job_level_id = ? and country_code = ? and effective_to is null)",
				jobLevelId, country, userId, jobLevelId, country);
		UUID bandId = jdbcTemplate.queryForObject(
				"select id from salary_schema.salary_bands where job_level_id = ? and country_code = ? "
						+ "and effective_to is null", UUID.class, jobLevelId, country);

		return new Fixture(bandId, jobLevelId, country, userId);
	}

}
