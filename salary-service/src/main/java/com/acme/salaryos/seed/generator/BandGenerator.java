package com.acme.salaryos.seed.generator;

import com.acme.salaryos.seed.SeedRandom;
import com.acme.salaryos.seed.generator.ReferenceDataGenerator.Country;
import com.acme.salaryos.seed.generator.ReferenceDataGenerator.JobFamilySeed;
import com.acme.salaryos.seed.generator.ReferenceDataGenerator.JobLevel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per (job level × country), two versions (2024 closed, 2026 open) — backend doc §9. Nearly every
 * combination gets a band; the handful deliberately left unbanded are chosen by a fixed rule
 * (L6/L7 in Ireland and Poland — the two lowest-weighted countries at the two rarest levels), not
 * a coin flip, because {@link EmployeeGenerator}'s employee-country/level draw is independent per
 * employee: a per-combination 50/50 chance leaves roughly half the *population* unbanded (~5,000
 * employees), wildly over the doc's "40 with a level/country combination that has no band"
 * anomaly. Skipping only the lowest-population combinations lands close to that target instead —
 * {@code L6,L7 × IE,PL} carries an expected ~36 of the 10,000 employees.
 */
@Component
public class BandGenerator {

	private static final LocalDate V1_START = LocalDate.of(2024, 1, 1);
	private static final LocalDate V2_START = LocalDate.of(2026, 1, 1);

	/** Approximate local-currency-per-USD multipliers — real enough to make the band numbers look
	 * plausible on screen (INR/BRL genuinely reading as six/five figures), not a currency-desk feed. */
	private static final Map<String, Double> FX_VS_USD = Map.of(
			"US", 1.0, "GB", 0.79, "DE", 0.92, "IN", 83.0, "SG", 1.34, "BR", 5.0, "PL", 4.0, "IE", 0.92);

	/** USD-equivalent annual mid-point by sort order (L1..L7) — the pyramid's pay ladder. */
	private static final double[] LEVEL_MID_USD = { 55000, 70000, 90000, 115000, 145000, 185000, 230000 };

	/** The deliberately-unbanded combinations: senior levels (low headcount weight) in the two
	 * lowest-headcount-weight countries, so the resulting no-band population lands near ~40. */
	private static final java.util.Set<String> UNBANDED_COUNTRIES = java.util.Set.of("IE", "PL");

	private final JdbcTemplate jdbc;

	public BandGenerator(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Band(UUID id, UUID jobLevelId, String countryCode, String currency,
			BigDecimal min, BigDecimal mid, BigDecimal max) {
	}

	/** Keyed by "jobLevelId|countryCode" → the currently open (2026) version, the one every
	 * compensation/employee lookup actually uses. Absent key = deliberately unbanded combination. */
	public Map<String, Band> seedBands(
			SeedRandom random, List<Country> countries, List<JobFamilySeed> families, UUID createdBy) {
		Map<String, Band> openBands = new HashMap<>();
		List<Object[]> rows = new ArrayList<>();

		for (JobFamilySeed familySeed : families) {
			for (JobLevel level : familySeed.levels()) {
				double midUsd = LEVEL_MID_USD[level.sortOrder() - 1];
				for (Country country : countries) {
					if (level.sortOrder() >= 6 && UNBANDED_COUNTRIES.contains(country.code())) {
						continue; // deliberately no band for this combination
					}
					double fx = FX_VS_USD.get(country.code());
					BigDecimal mid = usdToLocal(midUsd, fx);
					BigDecimal min = usdToLocal(midUsd * 0.82, fx);
					BigDecimal max = usdToLocal(midUsd * 1.18, fx);

					UUID v1Id = random.uuid();
					UUID v2Id = random.uuid();
					rows.add(new Object[] {
							v1Id, level.id(), country.code(), country.currency(), min, mid, max,
							V1_START, V2_START, createdBy, null });
					// The 2026 version drifts slightly from 2024 — a real band review, not a copy.
					double drift = 1.0 + (random.nextDouble() * 0.08); // 0–8% up
					BigDecimal min2 = mid(min, drift);
					BigDecimal mid2 = mid(mid, drift);
					BigDecimal max2 = mid(max, drift);
					rows.add(new Object[] {
							v2Id, level.id(), country.code(), country.currency(), min2, mid2, max2,
							V2_START, null, createdBy, null });

					openBands.put(level.id() + "|" + country.code(),
							new Band(v2Id, level.id(), country.code(), country.currency(), min2, mid2, max2));
				}
			}
		}

		com.acme.salaryos.seed.JdbcBatch.insert(jdbc, 
				"insert into salary_schema.salary_bands "
						+ "(id, job_level_id, country_code, currency, min_amount, mid_amount, max_amount, "
						+ "effective_from, effective_to, created_by, note) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				rows);
		return openBands;
	}

	private BigDecimal usdToLocal(double usdAmount, double fx) {
		return BigDecimal.valueOf(usdAmount * fx).setScale(0, RoundingMode.HALF_UP).setScale(2);
	}

	private BigDecimal mid(BigDecimal amount, double drift) {
		return amount.multiply(BigDecimal.valueOf(drift)).setScale(2, RoundingMode.HALF_UP);
	}

}
