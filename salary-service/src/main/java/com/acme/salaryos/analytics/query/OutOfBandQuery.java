package com.acme.salaryos.analytics.query;

import com.acme.salaryos.analytics.dto.OutOfBandRow;
import com.acme.salaryos.common.money.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * FR-6.2: employees paid below their band's minimum or above its maximum. {@code band_status} is
 * already precomputed on {@code employee_current_comp} (P5.1/P5.2), so this is a straight filter,
 * not a recomputation — comparing {@code annual_base_amount} against the band, both in the same
 * native currency (the band's currency is the location's pay currency; see
 * {@code EffectiveDating.compaRatio}, which compares the same two figures the same way).
 * {@code NO_BAND} is deliberately excluded — "no band" is a coverage gap, not a "paid outside the
 * band" anomaly, same distinction P6.1's mandatory-note rule already draws.
 */
@Component
public class OutOfBandQuery {

	private final JdbcTemplate jdbcTemplate;

	public OutOfBandQuery(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private static final String ROWS_SQL = """
			SELECT e.id AS employee_id, e.first_name, e.last_name, e.employee_number,
			       e.department_id, e.location_id, e.job_level_id,
			       c.band_status, c.annual_base_amount, sb.currency,
			       sb.min_amount, sb.mid_amount, sb.max_amount,
			       CASE WHEN c.band_status = 'BELOW_MIN' THEN sb.min_amount - c.annual_base_amount
			            ELSE c.annual_base_amount - sb.max_amount END AS gap_amount
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e     ON e.id = c.employee_id
			  JOIN salary_schema.salary_bands sb ON sb.id = c.band_id
			 WHERE c.band_status IN ('BELOW_MIN', 'ABOVE_MAX')
			 ORDER BY gap_amount DESC
			""";

	private static final String COUNT_SQL = """
			SELECT c.band_status, count(*) AS n
			  FROM salary_schema.employee_current_comp c
			 WHERE c.band_status IN ('BELOW_MIN', 'ABOVE_MAX')
			 GROUP BY c.band_status
			""";

	/** In {@code baseCurrency} (CLAUDE.md §6.4 — each row's own already-pinned FX rate, implicit in
	 * {@code normalized_annual_base}, never a live one). Zero if nobody is below minimum. */
	private static final String TOTAL_COST_TO_MINIMUM_SQL = """
			SELECT coalesce(sum(
			         c.normalized_annual_base * (sb.min_amount - c.annual_base_amount) / c.annual_base_amount
			       ), 0) AS total
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.salary_bands sb ON sb.id = c.band_id
			 WHERE c.band_status = 'BELOW_MIN'
			""";

	public List<OutOfBandRow> rows() {
		return jdbcTemplate.query(ROWS_SQL, (rs, rowNum) -> new OutOfBandRow(
				(UUID) rs.getObject("employee_id"),
				rs.getString("first_name"), rs.getString("last_name"), rs.getString("employee_number"),
				(UUID) rs.getObject("department_id"), (UUID) rs.getObject("location_id"), (UUID) rs.getObject("job_level_id"),
				rs.getString("band_status"),
				new Money(rs.getBigDecimal("annual_base_amount"), rs.getString("currency")),
				new Money(rs.getBigDecimal("min_amount"), rs.getString("currency")),
				new Money(rs.getBigDecimal("mid_amount"), rs.getString("currency")),
				new Money(rs.getBigDecimal("max_amount"), rs.getString("currency")),
				new Money(rs.getBigDecimal("gap_amount"), rs.getString("currency"))));
	}

	public int countByStatus(String status) {
		return jdbcTemplate.query(COUNT_SQL, rs -> {
			while (rs.next()) {
				if (status.equals(rs.getString("band_status"))) {
					return rs.getInt("n");
				}
			}
			return 0;
		});
	}

	public BigDecimal totalCostToMinimum() {
		return jdbcTemplate.queryForObject(TOTAL_COST_TO_MINIMUM_SQL, BigDecimal.class);
	}

}
