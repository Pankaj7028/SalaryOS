package com.acme.salaryos.market.service;

import com.acme.salaryos.common.money.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * P11.6: the most recently imported market median for a (job level, country), for the tick on
 * {@code <BandBar>} and the "band mid vs market p50" figure in band health.
 *
 * <p><b>Batched, because the caller is a list.</b> A page of 50 employees touches up to 50 distinct
 * bands, and looking each one up on its own would put an N+1 behind the screen this product opens
 * on. One query per page instead, keyed on the pair the {@code market_data_points_lookup_idx} index
 * is built for.
 *
 * <p><b>Most recent month wins, and only one row per pair is returned.</b> Several sources may
 * publish for the same level and country; {@code DISTINCT ON} takes the newest, then the
 * alphabetically first source to break a tie deterministically. A benchmark that changed depending
 * on which row the database happened to return first would be worse than no benchmark — CLAUDE.md
 * §6.4's "run it twice, get the same number" applies to survey data exactly as it does to FX.
 *
 * <p><b>Currency is the caller's problem to check, and this returns the survey's own.</b> The
 * comparison only means anything when the band and the survey are in the same currency; converting
 * would pin a benchmark to one month's FX rate and make it drift for reasons unrelated to the
 * market (P11.5). Callers drop the figure when the currencies differ rather than converting it.
 */
@Component
public class MarketBenchmarkLookup {

	private final JdbcTemplate jdbcTemplate;

	public MarketBenchmarkLookup(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** The (job level, country) pair a benchmark is keyed by. */
	public record Scope(UUID jobLevelId, String countryCode) {
	}

	/**
	 * Latest p50 per requested scope, in the survey's own currency. Scopes with no imported data
	 * are simply absent from the map — the common case, since Salary OS ships the seam and not a
	 * dataset.
	 */
	public Map<Scope, Money> latestP50(Collection<Scope> scopes) {
		Set<Scope> wanted = new HashSet<>(scopes);
		if (wanted.isEmpty()) {
			return Map.of();
		}

		List<UUID> levelIds = wanted.stream().map(Scope::jobLevelId).distinct().toList();
		String placeholders = String.join(",", levelIds.stream().map(id -> "?").toList());

		// Schema-qualified per CLAUDE.md §6.5. Filtered on job level only and narrowed to the exact
		// pairs in Java: the index leads on job_level_id, and a country IN-list as well would not
		// narrow it further on any realistic import.
		String sql = "select distinct on (m.job_level_id, m.country_code) "
				+ "       m.job_level_id, m.country_code, m.p50_amount, m.currency "
				+ "  from salary_schema.market_data_points m "
				+ " where m.job_level_id in (" + placeholders + ") "
				+ " order by m.job_level_id, m.country_code, m.effective_month desc, m.source asc";

		Map<Scope, Money> found = new HashMap<>();
		jdbcTemplate.query(sql, levelIds.toArray(), rs -> {
			Scope scope = new Scope((UUID) rs.getObject("job_level_id"), rs.getString("country_code").trim());
			if (wanted.contains(scope)) {
				found.put(scope, new Money(rs.getBigDecimal("p50_amount"), rs.getString("currency").trim()));
			}
		});
		return found;
	}

	/** Single-scope convenience for the detail screens, which look at one band at a time. */
	public Money latestP50(UUID jobLevelId, String countryCode) {
		if (jobLevelId == null || countryCode == null) {
			return null;
		}
		return latestP50(List.of(new Scope(jobLevelId, countryCode))).get(new Scope(jobLevelId, countryCode));
	}

	/**
	 * The benchmark to actually show against a band: the survey figure, or null when the survey is
	 * denominated differently. Drawing a USD median on a GBP band scale would be a silent 25% lie
	 * on a scale the reader trusts to be one currency.
	 */
	public static Money sameCurrencyOnly(Money marketP50, String bandCurrency) {
		if (marketP50 == null || bandCurrency == null || !bandCurrency.equals(marketP50.currency())) {
			return null;
		}
		return marketP50;
	}

	/** {@code mid / p50 - 1}, or null when there is nothing comparable to divide by. */
	public static BigDecimal midVsMarket(BigDecimal bandMid, Money marketP50) {
		if (bandMid == null || marketP50 == null || marketP50.amount().signum() == 0) {
			return null;
		}
		return bandMid.divide(marketP50.amount(), 4, java.math.RoundingMode.HALF_UP)
				.subtract(BigDecimal.ONE);
	}

}
