package com.acme.salaryos.analytics.query;

import com.acme.salaryos.analytics.dto.HeadcountGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FR-6.1's headcount half. {@code byCountry}/{@code byDepartment}/{@code byLevel} join through
 * {@code employee_current_comp} (so a terminated employee, whose row is deleted at P5.2, is
 * excluded automatically — same reasoning as {@link PayrollCostQuery}); {@code byStatus} queries
 * {@code employees} directly so a terminated count still has somewhere to be seen (FR-6.8).
 */
@Component
public class HeadcountQuery {

	private final JdbcTemplate jdbcTemplate;

	public HeadcountQuery(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private static final String OVERALL_SQL =
			"SELECT count(*) FROM salary_schema.employee_current_comp";

	private static final String BY_COUNTRY_SQL = """
			SELECT l.country_code AS key, co.name AS label, count(*) AS headcount
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e   ON e.id = c.employee_id
			  JOIN salary_schema.locations l   ON l.id = e.location_id
			  JOIN salary_schema.countries co  ON co.code = l.country_code
			 GROUP BY l.country_code, co.name
			 ORDER BY co.name
			""";

	private static final String BY_DEPARTMENT_SQL = """
			SELECT d.id::text AS key, d.name AS label, count(*) AS headcount
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e    ON e.id = c.employee_id
			  JOIN salary_schema.departments d  ON d.id = e.department_id
			 GROUP BY d.id, d.name
			 ORDER BY d.name
			""";

	private static final String BY_LEVEL_SQL = """
			SELECT jl.id::text AS key, jl.title AS label, count(*) AS headcount
			  FROM salary_schema.employee_current_comp c
			  JOIN salary_schema.employees e    ON e.id = c.employee_id
			  JOIN salary_schema.job_levels jl  ON jl.id = e.job_level_id
			 GROUP BY jl.id, jl.title, jl.sort_order
			 ORDER BY jl.sort_order
			""";

	private static final String BY_STATUS_SQL = """
			SELECT status AS key, status AS label, count(*) AS headcount
			  FROM salary_schema.employees
			 GROUP BY status
			 ORDER BY status
			""";

	public int overall() {
		return jdbcTemplate.queryForObject(OVERALL_SQL, Integer.class);
	}

	public List<HeadcountGroup> byCountry() {
		return jdbcTemplate.query(BY_COUNTRY_SQL, (rs, rowNum) -> new HeadcountGroup(rs.getString("key"), rs.getString("label"), rs.getInt("headcount")));
	}

	public List<HeadcountGroup> byDepartment() {
		return jdbcTemplate.query(BY_DEPARTMENT_SQL, (rs, rowNum) -> new HeadcountGroup(rs.getString("key"), rs.getString("label"), rs.getInt("headcount")));
	}

	public List<HeadcountGroup> byLevel() {
		return jdbcTemplate.query(BY_LEVEL_SQL, (rs, rowNum) -> new HeadcountGroup(rs.getString("key"), rs.getString("label"), rs.getInt("headcount")));
	}

	public List<HeadcountGroup> byStatus() {
		return jdbcTemplate.query(BY_STATUS_SQL, (rs, rowNum) -> new HeadcountGroup(rs.getString("key"), rs.getString("label"), rs.getInt("headcount")));
	}

}
