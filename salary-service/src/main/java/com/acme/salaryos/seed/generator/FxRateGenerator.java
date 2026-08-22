package com.acme.salaryos.seed.generator;

import com.acme.salaryos.seed.SeedRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 72 months × 7 pairs (backend doc §9): every non-{@code baseCurrency} currency in {@link
 * ReferenceDataGenerator#COUNTRIES}, plus {@code baseCurrency}→{@code baseCurrency} itself —
 * {@link com.acme.salaryos.compensation.effective.EffectiveDating#apply} pins a real row even for
 * a same-currency conversion (CLAUDE.md §6.4's "every comp record" is literal; P8.3's own
 * done-note found this the hard way). A random walk from a plausible start, not a constant —
 * {@code rate} is "USD per 1 unit of this currency," the multiplier {@code EffectiveDating}
 * actually applies.
 */
@Component
public class FxRateGenerator {

	private static final int MONTHS = 72;

	/** local-currency-per-USD at the start of the walk — the inverse of what gets stored
	 * ({@code rate} is USD-per-local), just easier to reason about in this human-readable form. */
	private static final Map<String, Double> LOCAL_PER_USD_START = Map.of(
			"GBP", 0.81, "EUR", 0.93, "INR", 82.0, "SGD", 1.36, "BRL", 5.1, "PLN", 4.1);

	private final JdbcTemplate jdbc;

	public FxRateGenerator(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record FxRate(UUID id, LocalDate rateMonth, String baseCurrency, BigDecimal rate) {
	}

	/** Keyed by "currency|yyyy-MM-01" → the rate. */
	public Map<String, FxRate> seedFxRates(SeedRandom random, String baseCurrency) {
		Map<String, FxRate> byKey = new HashMap<>();
		List<Object[]> rows = new ArrayList<>();

		YearMonth start = YearMonth.from(SeedRandom.SEED_AS_AT).minusMonths(MONTHS - 1);

		// baseCurrency -> baseCurrency is always exactly 1 — nothing to walk.
		for (int i = 0; i < MONTHS; i++) {
			LocalDate month = start.plusMonths(i).atDay(1);
			UUID id = random.uuid();
			BigDecimal rate = BigDecimal.ONE.setScale(8);
			rows.add(new Object[] { id, month, baseCurrency, baseCurrency, rate });
			byKey.put(baseCurrency + "|" + month, new FxRate(id, month, baseCurrency, rate));
		}

		for (var entry : LOCAL_PER_USD_START.entrySet()) {
			String currency = entry.getKey();
			double localPerUsd = entry.getValue();
			for (int i = 0; i < MONTHS; i++) {
				// Small monthly drift, compounding — a real walk, not noise around a fixed point.
				localPerUsd *= 1.0 + (random.nextDouble() - 0.5) * 0.03; // ±1.5% per month
				LocalDate month = start.plusMonths(i).atDay(1);
				UUID id = random.uuid();
				BigDecimal rate = BigDecimal.ONE.divide(BigDecimal.valueOf(localPerUsd), 8, RoundingMode.HALF_UP);
				rows.add(new Object[] { id, month, currency, baseCurrency, rate });
				byKey.put(currency + "|" + month, new FxRate(id, month, currency, rate));
			}
		}

		com.acme.salaryos.seed.JdbcBatch.insert(jdbc, 
				"insert into salary_schema.fx_rates (id, rate_month, base_currency, quote_currency, rate) "
						+ "values (?, ?, ?, ?, ?)",
				rows);
		return byKey;
	}

}
