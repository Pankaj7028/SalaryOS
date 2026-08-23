package com.acme.salaryos.analytics.query;

import com.acme.salaryos.analytics.dto.AnalyticsBasis;
import com.acme.salaryos.analytics.dto.PayrollCostGroup;
import com.acme.salaryos.common.money.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * FR-6.1: total annualised base pay, broken down by country, department, and level — headcount
 * and average alongside every total. Aggregation happens in the database (backend doc §6); this
 * class never loads a row per employee to sum in Java.
 *
 * <p>Joins from {@code employee_current_comp}, never {@code employees} alone — a terminated
 * employee has no row there (P5.2's projector deletes it on termination), so this query excludes
 * them from every total for free, with no explicit status filter needed.
 *
 * <p><strong>P10.6 — the two bases.</strong> {@code BASE} sums {@code normalized_annual_base},
 * exactly as before. {@code TOTAL_TARGET_CASH} adds each record's recurring components. Components
 * carry their own currency and have no normalised column of their own, so each is converted with
 * the rate pinned for <em>its own currency in the month that record pinned</em> — never a live
 * rate, never today's month. That keeps CLAUDE.md §6.4's guarantee intact: this report run twice
 * returns the same number. Every currency in use has a rate row for every month, including the
 * identity USD→USD row, so the join cannot silently drop a component.
 */
@Component
public class PayrollCostQuery {

	private final JdbcTemplate jdbcTemplate;

	public PayrollCostQuery(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * The per-employee value being aggregated. For {@code BASE} this is one column; for
	 * {@code TOTAL_TARGET_CASH} it is that column plus the correlated component sum below.
	 */
	private static final String BASE_VALUE = "c.normalized_annual_base";

	private static final String TOTAL_TARGET_CASH_VALUE = "(c.normalized_annual_base + comps.component_total)";

	/**
	 * Only recurring components count toward target cash — a one-off payment is not part of what
	 * this person is paid annually, and including it would make two runs of the same report differ
	 * as one-offs come and go.
	 *
	 * <p>Schema-qualified on every table (CLAUDE.md §6.5), including inside this fragment —
	 * {@code NativeQuerySchemaQualificationTest} scans {@code *SQL*} constants, and a fragment that
	 * is concatenated in still resolves against the connection's {@code search_path} at runtime.
	 */
	private static final String COMPONENTS_JOIN_SQL = """
			  JOIN salary_schema.compensation_records r ON r.id = c.compensation_record_id
			  JOIN salary_schema.fx_rates rf            ON rf.id = r.fx_rate_id
			  LEFT JOIN LATERAL (
			      SELECT coalesce(sum(cc.amount * ccf.rate), 0) AS component_total
			        FROM salary_schema.compensation_components cc
			        JOIN salary_schema.fx_rates ccf
			          ON ccf.base_currency  = cc.currency
			         AND ccf.quote_currency = rf.quote_currency
			         AND ccf.rate_month     = rf.rate_month
			       WHERE cc.compensation_record_id = c.compensation_record_id
			         AND cc.is_recurring
			  ) comps ON true
			""";

	private static final String OVERALL_SQL = """
			SELECT count(*)                  AS headcount,
			       coalesce(sum(%1$s), 0)    AS total,
			       coalesce(avg(%1$s), 0)    AS average
			  FROM salary_schema.employee_current_comp c
			%2$s
			""";

	private static final String BY_COUNTRY_SQL = """
			SELECT l.country_code AS key, co.name AS label,
			       count(*) AS headcount,
			       sum(%1$s) AS total,
			       avg(%1$s) AS average
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e   ON e.id = c.employee_id
			  JOIN salary_schema.locations l   ON l.id = e.location_id
			  JOIN salary_schema.countries co  ON co.code = l.country_code
			%2$s
			 GROUP BY l.country_code, co.name
			 ORDER BY co.name
			""";

	private static final String BY_DEPARTMENT_SQL = """
			SELECT d.id::text AS key, d.name AS label,
			       count(*) AS headcount,
			       sum(%1$s) AS total,
			       avg(%1$s) AS average
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e    ON e.id = c.employee_id
			  JOIN salary_schema.departments d  ON d.id = e.department_id
			%2$s
			 GROUP BY d.id, d.name
			 ORDER BY d.name
			""";

	private static final String BY_LEVEL_SQL = """
			SELECT jl.id::text AS key, jl.title AS label,
			       count(*) AS headcount,
			       sum(%1$s) AS total,
			       avg(%1$s) AS average
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e    ON e.id = c.employee_id
			  JOIN salary_schema.job_levels jl  ON jl.id = e.job_level_id
			%2$s
			 GROUP BY jl.id, jl.title, jl.sort_order
			 ORDER BY jl.sort_order
			""";

	private static final String TERMINATED_COUNT_SQL =
			"SELECT count(*) FROM salary_schema.employees WHERE status = 'TERMINATED'";

	/**
	 * Both substitutions are compile-time constants selected by an enum — no caller string ever
	 * reaches the SQL, so there is no injection surface here despite the formatting.
	 */
	private static String sql(String template, AnalyticsBasis basis) {
		return basis == AnalyticsBasis.TOTAL_TARGET_CASH
				? template.formatted(TOTAL_TARGET_CASH_VALUE, COMPONENTS_JOIN_SQL)
				: template.formatted(BASE_VALUE, "");
	}

	public PayrollCostGroup overall(String currency, AnalyticsBasis basis) {
		return jdbcTemplate.queryForObject(sql(OVERALL_SQL, basis),
				(rs, rowNum) -> toGroup(rs, "ALL", "All employees", currency));
	}

	public List<PayrollCostGroup> byCountry(String currency, AnalyticsBasis basis) {
		return jdbcTemplate.query(sql(BY_COUNTRY_SQL, basis),
				(rs, rowNum) -> toGroup(rs, rs.getString("key"), rs.getString("label"), currency));
	}

	public List<PayrollCostGroup> byDepartment(String currency, AnalyticsBasis basis) {
		return jdbcTemplate.query(sql(BY_DEPARTMENT_SQL, basis),
				(rs, rowNum) -> toGroup(rs, rs.getString("key"), rs.getString("label"), currency));
	}

	public List<PayrollCostGroup> byLevel(String currency, AnalyticsBasis basis) {
		return jdbcTemplate.query(sql(BY_LEVEL_SQL, basis),
				(rs, rowNum) -> toGroup(rs, rs.getString("key"), rs.getString("label"), currency));
	}

	public int terminatedCount() {
		return jdbcTemplate.queryForObject(TERMINATED_COUNT_SQL, Integer.class);
	}

	private PayrollCostGroup toGroup(ResultSet rs, String key, String label, String currency) throws SQLException {
		int headcount = rs.getInt("headcount");
		BigDecimal total = rs.getBigDecimal("total");
		BigDecimal average = rs.getBigDecimal("average");
		return new PayrollCostGroup(
				key, label, headcount,
				new Money(total == null ? BigDecimal.ZERO.setScale(2) : total, currency),
				new Money(average == null ? BigDecimal.ZERO.setScale(2) : average.setScale(2, RoundingMode.HALF_UP), currency));
	}

}
