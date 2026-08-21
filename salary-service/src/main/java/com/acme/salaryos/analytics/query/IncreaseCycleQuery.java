package com.acme.salaryos.analytics.query;

import com.acme.salaryos.analytics.dto.IncreaseCycleReasonRow;
import com.acme.salaryos.common.money.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * FR-6.5: what a merit cycle cost. Only {@code APPLIED} changes count (CLAUDE.md §8 — an
 * {@code APPROVED} change with a future effective date is a promise, not spend). Joins each
 * change to the ledger row it actually produced via {@code applied_record_id} — direct, not a
 * {@code change_id} lookup — to reuse that row's already-pinned FX rate (implicit in the ratio of
 * its {@code normalized_annual_base} to its own {@code annual_base_amount}) for normalising the
 * delta, same trick {@code OutOfBandQuery.totalCostToMinimum} uses.
 */
@Component
public class IncreaseCycleQuery {

	private final JdbcTemplate jdbcTemplate;

	public IncreaseCycleQuery(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private static final String OVERALL_SQL = """
			SELECT count(*) AS n,
			       coalesce(sum(cr.normalized_annual_base
			                    - cc.current_base_amount * (cr.normalized_annual_base / cr.annual_base_amount)), 0) AS total,
			       avg((cc.new_base_amount - cc.current_base_amount) / cc.current_base_amount) AS avg_pct,
			       percentile_cont(0.5) WITHIN GROUP (
			         ORDER BY (cc.new_base_amount - cc.current_base_amount) / cc.current_base_amount) AS median_pct
			  FROM salary_schema.compensation_changes cc
			  JOIN salary_schema.compensation_records cr ON cr.id = cc.applied_record_id
			 WHERE cc.status = 'APPLIED'
			   AND cc.effective_date BETWEEN ? AND ?
			""";

	private static final String BY_REASON_SQL = """
			SELECT cc.change_reason AS reason, count(*) AS n,
			       sum(cr.normalized_annual_base
			           - cc.current_base_amount * (cr.normalized_annual_base / cr.annual_base_amount)) AS total,
			       avg((cc.new_base_amount - cc.current_base_amount) / cc.current_base_amount) AS avg_pct,
			       percentile_cont(0.5) WITHIN GROUP (
			         ORDER BY (cc.new_base_amount - cc.current_base_amount) / cc.current_base_amount) AS median_pct
			  FROM salary_schema.compensation_changes cc
			  JOIN salary_schema.compensation_records cr ON cr.id = cc.applied_record_id
			 WHERE cc.status = 'APPLIED'
			   AND cc.effective_date BETWEEN ? AND ?
			 GROUP BY cc.change_reason
			 ORDER BY cc.change_reason
			""";

	public record Overall(int count, BigDecimal total, BigDecimal avgPercent, BigDecimal medianPercent) {
	}

	public Overall overall(LocalDate fromDate, LocalDate toDate) {
		return jdbcTemplate.queryForObject(OVERALL_SQL, (rs, rowNum) -> new Overall(
				rs.getInt("n"), rs.getBigDecimal("total"), rs.getBigDecimal("avg_pct"), rs.getBigDecimal("median_pct")),
				fromDate, toDate);
	}

	public List<IncreaseCycleReasonRow> byReason(LocalDate fromDate, LocalDate toDate, String baseCurrency) {
		return jdbcTemplate.query(BY_REASON_SQL, (rs, rowNum) -> toRow(rs, baseCurrency), fromDate, toDate);
	}

	private IncreaseCycleReasonRow toRow(ResultSet rs, String baseCurrency) throws SQLException {
		return new IncreaseCycleReasonRow(
				rs.getString("reason"), rs.getInt("n"), new Money(rs.getBigDecimal("total"), baseCurrency),
				rs.getBigDecimal("avg_pct"), rs.getBigDecimal("median_pct"));
	}

}
