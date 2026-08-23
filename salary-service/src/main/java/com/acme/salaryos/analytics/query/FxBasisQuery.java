package com.acme.salaryos.analytics.query;

import com.acme.salaryos.analytics.dto.FxBasis;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * FR-6.8. Reports which pinned FX rates underpin an analytics figure — see {@link FxBasis} for why
 * this is a span and not a single month.
 *
 * <p>Two populations, because the four money-carrying reports draw from two places.
 * {@code payroll-cost}, {@code out-of-band} and {@code pay-gap} all read the current-comp
 * projection, so their basis is the rates behind the ledger row each projection row points at.
 * {@code increase-cycle} reads the ledger rows produced by changes applied in a date window, so
 * its basis is scoped the same way.
 *
 * <p>Aggregation is in the database (backend doc §6) — this never loads a rate per employee to
 * count them in Java.
 */
@Component
public class FxBasisQuery {

	private final JdbcTemplate jdbcTemplate;

	public FxBasisQuery(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * Basis for the current-comp population. Joins through {@code compensation_record_id} because
	 * {@code employee_current_comp} carries the normalised figure but not the rate that produced
	 * it — {@code fx_rate_id} lives on {@code compensation_records}, where it is {@code NOT NULL},
	 * so every row in this population has exactly one governing rate and none can be missed.
	 *
	 * <p>Terminated employees have no projection row (P5.2's projector deletes it), so they are
	 * excluded here for free — the same way every other current-comp query excludes them.
	 */
	private static final String CURRENT_COMP_SQL = """
			SELECT count(DISTINCT f.id)         AS distinct_rates,
			       count(DISTINCT f.rate_month) AS months_spanned,
			       min(f.rate_month)            AS earliest_month,
			       max(f.rate_month)            AS latest_month
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.compensation_records r ON r.id = c.compensation_record_id
			  JOIN salary_schema.fx_rates f             ON f.id = r.fx_rate_id
			""";

	/** Basis for the ledger rows written by changes applied in a window — mirrors {@code IncreaseCycleQuery}'s own scoping exactly. */
	private static final String APPLIED_CHANGES_SQL = """
			SELECT count(DISTINCT f.id)         AS distinct_rates,
			       count(DISTINCT f.rate_month) AS months_spanned,
			       min(f.rate_month)            AS earliest_month,
			       max(f.rate_month)            AS latest_month
			  FROM salary_schema.compensation_changes cc
			  JOIN salary_schema.compensation_records r ON r.id = cc.applied_record_id
			  JOIN salary_schema.fx_rates f             ON f.id = r.fx_rate_id
			 WHERE cc.status = 'APPLIED'
			   AND cc.effective_date BETWEEN ? AND ?
			""";

	public FxBasis forCurrentComp() {
		return jdbcTemplate.queryForObject(CURRENT_COMP_SQL, FxBasisQuery::toBasis);
	}

	public FxBasis forAppliedChanges(LocalDate fromDate, LocalDate toDate) {
		return jdbcTemplate.queryForObject(APPLIED_CHANGES_SQL, FxBasisQuery::toBasis, fromDate, toDate);
	}

	/**
	 * An empty population aggregates to {@code count = 0} with {@code NULL} min/max — a real state
	 * (a brand-new database, or a date window with no applied changes), not an error. {@code
	 * getDate} returns {@code null} there rather than throwing, so the span is simply absent.
	 */
	private static FxBasis toBasis(ResultSet rs, int rowNum) throws SQLException {
		Date earliest = rs.getDate("earliest_month");
		Date latest = rs.getDate("latest_month");
		return new FxBasis(
				rs.getInt("distinct_rates"),
				rs.getInt("months_spanned"),
				earliest == null ? null : earliest.toLocalDate(),
				latest == null ? null : latest.toLocalDate());
	}

}
