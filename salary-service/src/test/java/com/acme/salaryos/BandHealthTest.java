package com.acme.salaryos;

import com.acme.salaryos.analytics.dto.BandHealthResponse;
import com.acme.salaryos.analytics.dto.BandHealthRow;
import com.acme.salaryos.analytics.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P11.3's Verify clause: spread and progression reconcile against direct SQL, and a band whose
 * maximum sits below the next level's minimum is flagged.
 *
 * <p>Scoped assertions only — the shared Testcontainers container carries bands created by
 * {@code BandVersioningTest} and others.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class BandHealthTest {

	private static final String FIXTURE_FAMILY = "Band Health Fixture";

	@Autowired
	private AnalyticsService analyticsService;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * Seeds one family × one country with three ordered levels and a deliberate promotion cliff
	 * between L2 and L3 (L2 tops out at 90,000; L3 starts at 100,000).
	 *
	 * <p><strong>Why this exists:</strong> without it the whole class passed vacuously. The shared
	 * container held no in-force bands when this class ran, so every {@code allSatisfy} was
	 * satisfied by an empty list and the gap assertion compared 0 to 0. A green test over no rows
	 * proves nothing, and this feature's entire point is detecting a structure problem — so the
	 * test has to contain one.
	 */
	@BeforeEach
	void seedOneFamilyWithAKnownGap() {
		Integer existing = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM salary_schema.job_families WHERE code = 'BANDHEALTH'", Integer.class);
		if (existing != null && existing > 0) {
			return;
		}

		String familyId = jdbcTemplate.queryForObject(
				"INSERT INTO salary_schema.job_families (name, code) VALUES (?, 'BANDHEALTH') RETURNING id::text",
				String.class, FIXTURE_FAMILY);

		String userId = jdbcTemplate.queryForObject(
				"INSERT INTO salary_schema.users (email, full_name, password_hash, role) "
						+ "VALUES ('bandhealth-fixture@acme.test', 'Band Health', '{noop}x', 'HR_ADMIN') "
						+ "RETURNING id::text",
				String.class);

		// `countries` has no migration-seeded rows — they come from the seed profile, which does not
		// run under test. So the fixture brings its own rather than assuming one exists (the first
		// version of this test did, and silently produced an empty result set instead of failing).
		//
		// NOT 'ZZ': V2ReferenceDataMigrationTest uses that code as its deliberately-invalid country
		// to assert a foreign-key violation, and creating it for real made that insert succeed and
		// its test fail. Any code used here becomes valid for every class sharing this container.
		String country = "QX";
		jdbcTemplate.update(
				"INSERT INTO salary_schema.countries (code, name, default_currency) "
						+ "VALUES (?, 'Band Health Land', 'USD') ON CONFLICT (code) DO NOTHING",
				country);

		// L1 50–60–70k, L2 70–80–90k (overlaps L1 — healthy), L3 100–110–120k (gap above L2 — a cliff)
		int[][] bands = {{1, 50000, 60000, 70000}, {2, 70000, 80000, 90000}, {3, 100000, 110000, 120000}};
		for (int[] band : bands) {
			String levelId = jdbcTemplate.queryForObject(
					"INSERT INTO salary_schema.job_levels (job_family_id, level_code, title, sort_order) "
							+ "VALUES (?::uuid, ?, ?, ?) RETURNING id::text",
					String.class, familyId, "L" + band[0], "Fixture L" + band[0], band[0]);

			jdbcTemplate.update(
					"INSERT INTO salary_schema.salary_bands "
							+ "(job_level_id, country_code, currency, min_amount, mid_amount, max_amount, "
							+ " effective_from, created_by) "
							+ "VALUES (?::uuid, ?, 'USD', ?, ?, ?, DATE '2026-01-01', ?::uuid)",
					levelId, country, band[1], band[2], band[3], userId);
		}
	}

	/** Only in-force bands are judged; a superseded version is history, not a live problem. */
	@Test
	void countsOnlyInForceBands() {
		BandHealthResponse response = analyticsService.bandHealth();

		Integer inForce = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM salary_schema.salary_bands WHERE effective_to IS NULL", Integer.class);

		assertThat(response.inForceBands()).isEqualTo(inForce);
		assertThat(response.rows()).hasSize(inForce);
	}

	/** rangeSpread = max/min − 1, recomputed here from the row's own money figures. */
	@Test
	void rangeSpreadIsMaxOverMinMinusOne() {
		assertThat(analyticsService.bandHealth().rows()).isNotEmpty().allSatisfy(row -> {
			BigDecimal expected = row.max().amount()
					.divide(row.min().amount(), 4, RoundingMode.HALF_UP)
					.subtract(BigDecimal.ONE);
			assertThat(row.rangeSpread()).isEqualByComparingTo(expected);
		});
	}

	/** A band's own min/mid/max must stay ordered — the band_ordered CHECK should guarantee it. */
	@Test
	void everyBandIsInternallyOrdered() {
		assertThat(analyticsService.bandHealth().rows()).isNotEmpty().allSatisfy(row -> {
			assertThat(row.min().amount()).isLessThanOrEqualTo(row.mid().amount());
			assertThat(row.mid().amount()).isLessThanOrEqualTo(row.max().amount());
			assertThat(row.rangeSpread()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
		});
	}

	/**
	 * The lowest level in each family+country has nothing beneath it, so progression is null there
	 * and non-null everywhere else. A null anywhere else means the window partition is wrong.
	 */
	@Test
	void progressionIsNullOnlyForTheLowestLevelInEachFamilyAndCountry() {
		long nullProgressions = analyticsService.bandHealth().rows().stream()
				.filter(row -> row.midpointProgression() == null)
				.count();

		Integer distinctFamilyCountryPairs = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM ("
						+ "  SELECT jl.job_family_id, b.country_code"
						+ "    FROM salary_schema.salary_bands b"
						+ "    JOIN salary_schema.job_levels jl ON jl.id = b.job_level_id"
						+ "   WHERE b.effective_to IS NULL"
						+ "   GROUP BY jl.job_family_id, b.country_code"
						+ ") pairs", Integer.class);

		assertThat(nullProgressions).isEqualTo(distinctFamilyCountryPairs.longValue());
	}

	/** The seeded L2→L3 cliff must actually be found — this is the check the feature exists for. */
	@Test
	void detectsTheSeededPromotionCliffAndNotTheHealthyOverlap() {
		var fixture = analyticsService.bandHealth().rows().stream()
				.filter(row -> row.jobFamily().equals(FIXTURE_FAMILY))
				.toList();

		assertThat(fixture).hasSize(3);
		assertThat(fixture).filteredOn(row -> row.levelCode().equals("L2"))
				.allMatch(row -> !row.gapToPreviousLevel(), "L1->L2 overlaps, which is healthy");
		assertThat(fixture).filteredOn(row -> row.levelCode().equals("L3"))
				.allMatch(BandHealthRow::gapToPreviousLevel, "L2 max 90k < L3 min 100k is a cliff");
	}

	/** Midpoint progression across the seeded levels: 60k→80k is +33.33%, 80k→110k is +37.5%. */
	@Test
	void midpointProgressionIsComputedAgainstThePreviousLevelInTheSameFamily() {
		var fixture = analyticsService.bandHealth().rows().stream()
				.filter(row -> row.jobFamily().equals(FIXTURE_FAMILY))
				.toList();

		BandHealthRow l2 = fixture.stream().filter(r -> r.levelCode().equals("L2")).findFirst().orElseThrow();
		BandHealthRow l3 = fixture.stream().filter(r -> r.levelCode().equals("L3")).findFirst().orElseThrow();

		assertThat(l2.midpointProgression()).isEqualByComparingTo(new BigDecimal("0.3333"));
		assertThat(l3.midpointProgression()).isEqualByComparingTo(new BigDecimal("0.3750"));
	}

	/** Incumbent counts come from the projection, so they must match it exactly. */
	@Test
	void incumbentCountsMatchTheProjection() {
		assertThat(analyticsService.bandHealth().rows()).isNotEmpty().allSatisfy(row -> {
			Integer actual = jdbcTemplate.queryForObject(
					"SELECT count(*) FROM salary_schema.employee_current_comp WHERE band_id = ?",
					Integer.class, row.bandId());
			assertThat(row.incumbents()).isEqualTo(actual);
		});
	}

	/** Summary counts are the headline — they have to agree with the detail behind them. */
	@Test
	void summaryCountsAgreeWithTheRows() {
		BandHealthResponse response = analyticsService.bandHealth();

		assertThat(response.bandsWithNoIncumbents())
				.isEqualTo((int) response.rows().stream().filter(r -> r.incumbents() == 0).count());
		assertThat(response.bandsWithGapToPreviousLevel())
				.isEqualTo((int) response.rows().stream().filter(BandHealthRow::gapToPreviousLevel).count());
		assertThat(response.staleBands())
				.isEqualTo((int) response.rows().stream()
						.filter(r -> r.monthsSinceVersioned() >= response.staleAfterMonths()).count());
	}

	/** Money never travels without its currency (CLAUDE.md §6.2), including here. */
	@Test
	void everyFigureCarriesItsCurrency() {
		assertThat(analyticsService.bandHealth().rows()).isNotEmpty().allSatisfy(row -> {
			assertThat(row.min().currency()).isNotBlank();
			assertThat(row.mid().currency()).isEqualTo(row.min().currency());
			assertThat(row.max().currency()).isEqualTo(row.min().currency());
		});
	}

}
