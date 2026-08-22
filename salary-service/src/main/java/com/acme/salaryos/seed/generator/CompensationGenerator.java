package com.acme.salaryos.seed.generator;

import com.acme.salaryos.compensation.effective.EffectiveDating;
import com.acme.salaryos.seed.SeedRandom;
import com.acme.salaryos.seed.generator.BandGenerator.Band;
import com.acme.salaryos.seed.generator.EmployeeGenerator.SeededEmployee;
import com.acme.salaryos.seed.generator.FxRateGenerator.FxRate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * ~40,000 compensation records + ~14,000 components (backend doc §9): 3–7 periods per employee
 * over up to six years, log-normal around band mid, with the deliberate anomalies FR-8.4 asks
 * for. Every record is written by hand here rather than through {@link EffectiveDating} — the
 * same insert-only shape and the exact same {@code compa_ratio}/{@code range_penetration}/{@code
 * band_status} formulas ({@link EffectiveDating#bandStatus}), just batched instead of one
 * transaction per row.
 *
 * <p>Simplifications, all deliberate: every seed record uses the CURRENT (2026) band version
 * even for a 2022-dated period (band history is only two versions deep; precise period-exact
 * snapshotting isn't worth the complexity for data whose job is exercising the UI); every period
 * is {@code payFrequency=ANNUAL}; no seed record links a {@code change_id} to a formal {@code
 * CompensationChange} row ({@link ChangeGenerator} seeds those independently — see its own
 * javadoc for why); no {@code CORRECTION} reason appears in seed history.
 */
@Slf4j
@Component
public class CompensationGenerator {

	private static final String[] RAISE_REASONS = {
			"MERIT", "MERIT", "MERIT", "MERIT", "MERIT", "MERIT",
			"PROMOTION", "PROMOTION",
			"MARKET_ADJUSTMENT", "MARKET_ADJUSTMENT",
			"ROLE_CHANGE", "LOCATION_CHANGE" };

	/** The "two countries" the below-band anomaly concentrates in (backend doc §9). */
	private static final Set<String> BELOW_MIN_COUNTRIES = Set.of("IN", "BR");
	private static final int BELOW_MIN_TARGET = 180;
	private static final int ABOVE_MAX_TARGET = 60;
	/** Applied to every period of a woman in this department — the deliberate ~7% adjusted
	 * equity gap (backend doc §9). Sales is a single job family, so its headcount concentrates
	 * into a shared handful of (level × country) cohorts, which is where the equity screen
	 * actually looks for the gap, rather than spreading thin across every department. */
	private static final String EQUITY_GAP_DEPARTMENT_CODE = "SALES";
	private static final BigDecimal EQUITY_GAP_MULTIPLIER = BigDecimal.valueOf(0.93);
	/** Chance a component-eligible period actually gets one -- see the call sites for why. */
	private static final double COMPONENT_CHANCE = 0.46;

	private final JdbcTemplate jdbc;

	public CompensationGenerator(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Anomalies(int noBand, int belowMin, int aboveMax, double compaRatioMin, double compaRatioMax) {
	}

	/** {@code currentAnnualBase}/{@code currentCurrency} are handed to {@link ChangeGenerator} —
	 * it needs "what does this person earn right now" to write a plausible proposal, without
	 * re-deriving it from the ledger itself. */
	public record CompensationResult(
			Anomalies anomalies, Map<UUID, BigDecimal> currentAnnualBase, Map<UUID, String> currentCurrency) {
	}

	public CompensationResult seedCompensation(
			SeedRandom random, List<SeededEmployee> employees, Map<String, Band> openBands,
			Map<String, FxRate> fxRatesByKey, Map<String, UUID> departmentIdByCode, Map<UUID, String> genderByEmployee,
			String baseCurrency, List<UUID> creatorUserIds) {

		UUID equityGapDepartmentId = departmentIdByCode.get(EQUITY_GAP_DEPARTMENT_CODE);

		List<SeededEmployee> banded = new ArrayList<>();
		int noBandCount = 0;
		for (SeededEmployee e : employees) {
			if (openBands.containsKey(e.jobLevelId() + "|" + e.countryCode())) {
				banded.add(e);
			}
			else {
				noBandCount++;
			}
		}

		Set<UUID> belowMinTargets = sample(random,
				banded.stream().filter(e -> BELOW_MIN_COUNTRIES.contains(e.countryCode())).toList(), BELOW_MIN_TARGET);
		Set<UUID> aboveMaxTargets = sample(random,
				banded.stream().filter(e -> e.levelSortOrder() >= 5).toList(), ABOVE_MAX_TARGET);

		List<Object[]> recordRows = new ArrayList<>();
		List<Object[]> componentRows = new ArrayList<>();
		List<Object[]> currentCompRows = new ArrayList<>();
		Map<UUID, BigDecimal> currentAnnualBase = new java.util.HashMap<>();
		Map<UUID, String> currentCurrency = new java.util.HashMap<>();
		double[] compaRatioRange = { Double.MAX_VALUE, -Double.MAX_VALUE };

		for (SeededEmployee employee : employees) {
			Band band = openBands.get(employee.jobLevelId() + "|" + employee.countryCode());
			String currency = currencyFor(employee.countryCode());
			boolean isEquityGapTarget = employee.departmentId().equals(equityGapDepartmentId)
					&& "F".equals(genderByEmployee.get(employee.id()));

			// The "six years of history" window is relative to THIS employee's own timeline end,
			// not always SEED_AS_AT -- a long-terminated employee's whole tenure can predate
			// SEED_AS_AT by more than six years, and clamping against today's date instead of
			// their own periodsEnd could push periodsStart past periodsEnd (a real bug this
			// surfaced: effective_from later than effective_to, rejected by comp_dates_ordered).
			LocalDate periodsEnd = employee.terminationDate() != null ? employee.terminationDate().plusDays(1) : SeedRandom.SEED_AS_AT;
			LocalDate periodsStart = maxDate(employee.hireDate(), periodsEnd.minusYears(6));
			int periodCount = random.nextInt(3, 7);
			List<LocalDate> starts = periodStarts(random, periodsStart, periodsEnd, periodCount);

			BigDecimal runningAnnualBase = band != null
					? random.logNormalAround(band.min(), band.mid(), band.max())
					: fallbackAmount(employee.levelSortOrder());
			UUID createdBy = random.pick(creatorUserIds);

			for (int i = 0; i < starts.size(); i++) {
				boolean isLast = i == starts.size() - 1;
				boolean isCurrent = isLast && employee.terminationDate() == null;

				if (i > 0) {
					double raise = 1.02 + random.nextDouble() * 0.10; // +2%..+12%
					runningAnnualBase = runningAnnualBase.multiply(BigDecimal.valueOf(raise)).setScale(2, RoundingMode.HALF_UP);
					// Compounding raises with no ceiling would let a long-tenured employee's compa-ratio
					// drift arbitrarily far above band mid over 6-7 periods, well past the doc's
					// "0.72-1.28" spread -- clamp ordinary drift to that range. The deliberate
					// belowMin/aboveMax overrides below run AFTER this and replace the value outright,
					// so the anomaly targets still land outside it as intended.
					if (band != null) {
						BigDecimal floor = band.mid().multiply(BigDecimal.valueOf(0.72)).setScale(2, RoundingMode.HALF_UP);
						BigDecimal ceiling = band.mid().multiply(BigDecimal.valueOf(1.28)).setScale(2, RoundingMode.HALF_UP);
						if (runningAnnualBase.compareTo(floor) < 0) {
							runningAnnualBase = floor;
						}
						else if (runningAnnualBase.compareTo(ceiling) > 0) {
							runningAnnualBase = ceiling;
						}
					}
				}
				if (isLast && band != null && belowMinTargets.contains(employee.id())) {
					runningAnnualBase = band.min().multiply(BigDecimal.valueOf(0.75 + random.nextDouble() * 0.20)).setScale(2, RoundingMode.HALF_UP);
				}
				else if (isLast && band != null && aboveMaxTargets.contains(employee.id())) {
					runningAnnualBase = band.max().multiply(BigDecimal.valueOf(1.03 + random.nextDouble() * 0.30)).setScale(2, RoundingMode.HALF_UP);
				}
				if (isEquityGapTarget) {
					runningAnnualBase = runningAnnualBase.multiply(EQUITY_GAP_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);
				}

				BigDecimal annualBase = runningAnnualBase;
				BigDecimal baseAmount = annualBase.multiply(employee.fte()).setScale(2, RoundingMode.HALF_UP);
				LocalDate effectiveFrom = starts.get(i);
				LocalDate effectiveTo = isLast ? (isCurrent ? null : periodsEnd) : starts.get(i + 1);
				String changeReason = i == 0 ? "INITIAL" : random.pick(RAISE_REASONS);

				FxRate fxRate = lookupFxRate(fxRatesByKey, currency, effectiveFrom);
				BigDecimal normalizedAnnualBase = annualBase.multiply(fxRate.rate()).setScale(2, RoundingMode.HALF_UP);
				BigDecimal compaRatio = band == null ? null : annualBase.divide(band.mid(), 4, RoundingMode.HALF_UP);
				BigDecimal rangePenetration = band == null ? null : rangePenetration(annualBase, band);

				UUID recordId = random.uuid();
				recordRows.add(new Object[] {
						recordId, employee.id(), effectiveFrom, effectiveTo, baseAmount, currency,
						"ANNUAL", annualBase, normalizedAnnualBase, baseCurrency, fxRate.id(),
						band == null ? null : band.id(), compaRatio, rangePenetration, changeReason, createdBy });

				// Not every eligible period carries a component -- attaching one to every L4+/SG/IN
				// period unconditionally produced ~31k rows against the doc's "~14,000"; COMPONENT_CHANCE
				// thins that down to roughly the right order of magnitude while keeping both kinds of
				// component genuinely present across periods, not only on the current one.
				if (employee.levelSortOrder() >= 4 && random.chance(COMPONENT_CHANCE)) {
					BigDecimal percent = BigDecimal.valueOf(0.15 + random.nextDouble() * 0.10).setScale(4, RoundingMode.HALF_UP);
					BigDecimal amount = annualBase.multiply(percent).setScale(2, RoundingMode.HALF_UP);
					componentRows.add(new Object[] { random.uuid(), recordId, "BONUS_TARGET", amount, currency, percent, true });
				}
				if (("SG".equals(employee.countryCode()) || "IN".equals(employee.countryCode())) && random.chance(COMPONENT_CHANCE)) {
					String componentType = random.chance(0.5) ? "HOUSING" : "TRANSPORT";
					double baseUnits = componentType.equals("HOUSING") ? 12000 : 3000;
					double localScale = "IN".equals(employee.countryCode()) ? 60 : 1.3;
					BigDecimal amount = BigDecimal.valueOf(baseUnits * localScale).setScale(2, RoundingMode.HALF_UP);
					componentRows.add(new Object[] { random.uuid(), recordId, componentType, amount, currency, null, true });
				}

				if (isCurrent) {
					if (compaRatio != null) {
						compaRatioRange[0] = Math.min(compaRatioRange[0], compaRatio.doubleValue());
						compaRatioRange[1] = Math.max(compaRatioRange[1], compaRatio.doubleValue());
					}
					String bandStatus = band == null ? "NO_BAND" : EffectiveDating.bandStatus(annualBase, toDomainBand(band));
					currentCompRows.add(new Object[] {
							employee.id(), recordId, baseAmount, currency, annualBase, normalizedAnnualBase,
							band == null ? null : band.id(), compaRatio, rangePenetration, bandStatus });
					currentAnnualBase.put(employee.id(), annualBase);
					currentCurrency.put(employee.id(), currency);
				}
			}
		}

		batchInsertRecords(recordRows);
		batchInsertComponents(componentRows);
		batchInsertCurrentComp(currentCompRows);

		log.info("Compensation: {} records, {} components, {} current-comp rows. Current-period compa-ratio range: {}..{}",
				recordRows.size(), componentRows.size(), currentCompRows.size(),
				String.format("%.2f", compaRatioRange[0]), String.format("%.2f", compaRatioRange[1]));

		Anomalies anomalies = new Anomalies(noBandCount, belowMinTargets.size(), aboveMaxTargets.size(), compaRatioRange[0], compaRatioRange[1]);
		return new CompensationResult(anomalies, currentAnnualBase, currentCurrency);
	}

	// -- helpers ------------------------------------------------------------------------------

	/** {@link EffectiveDating#bandStatus} takes the real domain type — a minimal stand-in built
	 * from just the three numbers it actually reads, not a full re-fetch from the database. */
	private com.acme.salaryos.band.domain.SalaryBand toDomainBand(Band band) {
		return com.acme.salaryos.band.domain.SalaryBand.builder()
				.minAmount(band.min()).midAmount(band.mid()).maxAmount(band.max()).build();
	}

	private LocalDate maxDate(LocalDate a, LocalDate b) {
		return a.isAfter(b) ? a : b;
	}

	private BigDecimal rangePenetration(BigDecimal annualBaseAmount, Band band) {
		BigDecimal range = band.max().subtract(band.min());
		if (range.signum() == 0) {
			return BigDecimal.ZERO;
		}
		return annualBaseAmount.subtract(band.min())
				.divide(range, 6, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.setScale(4, RoundingMode.HALF_UP);
	}

	private List<LocalDate> periodStarts(SeedRandom random, LocalDate from, LocalDate to, int count) {
		List<LocalDate> starts = new ArrayList<>();
		starts.add(from);
		for (int i = 1; i < count; i++) {
			starts.add(random.dateBetween(from, to));
		}
		starts.sort(LocalDate::compareTo);
		List<LocalDate> distinct = new ArrayList<>();
		for (LocalDate d : starts) {
			if (distinct.isEmpty() || !distinct.get(distinct.size() - 1).equals(d)) {
				distinct.add(d);
			}
		}
		if (distinct.get(0).isAfter(from)) {
			distinct.set(0, from);
		}
		return distinct;
	}

	private FxRate lookupFxRate(Map<String, FxRate> fxRatesByKey, String currency, LocalDate effectiveFrom) {
		YearMonth month = YearMonth.from(effectiveFrom);
		FxRate rate = fxRatesByKey.get(currency + "|" + month.atDay(1));
		if (rate != null) {
			return rate;
		}
		// Outside the 72-month window — periodsStart is already clamped to 6 years back, so this
		// is the defensive fallback (a leap-year/month-boundary edge), not the common path.
		YearMonth earliest = YearMonth.from(SeedRandom.SEED_AS_AT).minusMonths(71);
		FxRate fallback = fxRatesByKey.get(currency + "|" + earliest.atDay(1));
		if (fallback == null) {
			throw new IllegalStateException("No fx rate for " + currency + " near " + effectiveFrom);
		}
		return fallback;
	}

	private Set<UUID> sample(SeedRandom random, List<SeededEmployee> pool, int count) {
		List<SeededEmployee> shuffled = new ArrayList<>(pool);
		for (int i = shuffled.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			SeededEmployee tmp = shuffled.get(i);
			shuffled.set(i, shuffled.get(j));
			shuffled.set(j, tmp);
		}
		Set<UUID> ids = new HashSet<>();
		for (int i = 0; i < Math.min(count, shuffled.size()); i++) {
			ids.add(shuffled.get(i).id());
		}
		return ids;
	}

	private static final double[] LEVEL_MID_USD = { 55000, 70000, 90000, 115000, 145000, 185000, 230000 };

	private BigDecimal fallbackAmount(int levelSortOrder) {
		return BigDecimal.valueOf(LEVEL_MID_USD[levelSortOrder - 1]).setScale(2, RoundingMode.HALF_UP);
	}

	private String currencyFor(String countryCode) {
		return switch (countryCode) {
			case "US" -> "USD";
			case "GB" -> "GBP";
			case "DE", "IE" -> "EUR";
			case "IN" -> "INR";
			case "SG" -> "SGD";
			case "BR" -> "BRL";
			case "PL" -> "PLN";
			default -> throw new IllegalArgumentException("Unknown country: " + countryCode);
		};
	}

	private void batchInsertRecords(List<Object[]> rows) {
		batchInChunks(rows, 1000, chunk -> jdbc.batchUpdate(
				"insert into salary_schema.compensation_records (id, employee_id, effective_from, effective_to, "
						+ "base_amount, currency, pay_frequency, annual_base_amount, normalized_annual_base, "
						+ "base_currency, fx_rate_id, band_id, compa_ratio, range_penetration, change_reason, created_by) "
						+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				chunk));
	}

	private void batchInsertComponents(List<Object[]> rows) {
		batchInChunks(rows, 1000, chunk -> jdbc.batchUpdate(
				"insert into salary_schema.compensation_components "
						+ "(id, compensation_record_id, component_type, amount, currency, percent_of_base, is_recurring) "
						+ "values (?, ?, ?, ?, ?, ?, ?)",
				chunk));
	}

	private void batchInsertCurrentComp(List<Object[]> rows) {
		batchInChunks(rows, 1000, chunk -> jdbc.batchUpdate(
				"insert into salary_schema.employee_current_comp (employee_id, compensation_record_id, base_amount, "
						+ "currency, annual_base_amount, normalized_annual_base, band_id, compa_ratio, range_penetration, band_status) "
						+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				chunk));
	}

	private void batchInChunks(List<Object[]> rows, int chunkSize, Consumer<List<Object[]>> inserter) {
		for (int i = 0; i < rows.size(); i += chunkSize) {
			inserter.accept(rows.subList(i, Math.min(i + chunkSize, rows.size())));
		}
	}

}
