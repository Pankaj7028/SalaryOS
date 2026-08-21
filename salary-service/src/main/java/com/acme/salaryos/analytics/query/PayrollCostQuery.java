package com.acme.salaryos.analytics.query;

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
 */
@Component
public class PayrollCostQuery {

	private final JdbcTemplate jdbcTemplate;

	public PayrollCostQuery(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private static final String OVERALL_SQL = """
			SELECT count(*)                                    AS headcount,
			       coalesce(sum(c.normalized_annual_base), 0)  AS total,
			       coalesce(avg(c.normalized_annual_base), 0)  AS average
			  FROM salary_schema.employee_current_comp c
			""";

	private static final String BY_COUNTRY_SQL = """
			SELECT l.country_code AS key, co.name AS label,
			       count(*) AS headcount,
			       sum(c.normalized_annual_base) AS total,
			       avg(c.normalized_annual_base) AS average
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e   ON e.id = c.employee_id
			  JOIN salary_schema.locations l   ON l.id = e.location_id
			  JOIN salary_schema.countries co  ON co.code = l.country_code
			 GROUP BY l.country_code, co.name
			 ORDER BY co.name
			""";

	private static final String BY_DEPARTMENT_SQL = """
			SELECT d.id::text AS key, d.name AS label,
			       count(*) AS headcount,
			       sum(c.normalized_annual_base) AS total,
			       avg(c.normalized_annual_base) AS average
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e    ON e.id = c.employee_id
			  JOIN salary_schema.departments d  ON d.id = e.department_id
			 GROUP BY d.id, d.name
			 ORDER BY d.name
			""";

	private static final String BY_LEVEL_SQL = """
			SELECT jl.id::text AS key, jl.title AS label,
			       count(*) AS headcount,
			       sum(c.normalized_annual_base) AS total,
			       avg(c.normalized_annual_base) AS average
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e    ON e.id = c.employee_id
			  JOIN salary_schema.job_levels jl  ON jl.id = e.job_level_id
			 GROUP BY jl.id, jl.title, jl.sort_order
			 ORDER BY jl.sort_order
			""";

	private static final String TERMINATED_COUNT_SQL =
			"SELECT count(*) FROM salary_schema.employees WHERE status = 'TERMINATED'";

	public PayrollCostGroup overall(String currency) {
		return jdbcTemplate.queryForObject(OVERALL_SQL, (rs, rowNum) -> toGroup(rs, "ALL", "All employees", currency));
	}

	public List<PayrollCostGroup> byCountry(String currency) {
		return jdbcTemplate.query(BY_COUNTRY_SQL, (rs, rowNum) -> toGroup(rs, rs.getString("key"), rs.getString("label"), currency));
	}

	public List<PayrollCostGroup> byDepartment(String currency) {
		return jdbcTemplate.query(BY_DEPARTMENT_SQL, (rs, rowNum) -> toGroup(rs, rs.getString("key"), rs.getString("label"), currency));
	}

	public List<PayrollCostGroup> byLevel(String currency) {
		return jdbcTemplate.query(BY_LEVEL_SQL, (rs, rowNum) -> toGroup(rs, rs.getString("key"), rs.getString("label"), currency));
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
