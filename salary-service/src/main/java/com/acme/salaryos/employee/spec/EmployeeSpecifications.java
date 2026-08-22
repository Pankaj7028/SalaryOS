package com.acme.salaryos.employee.spec;

import com.acme.salaryos.compensation.domain.EmployeeCurrentComp;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.reference.domain.Location;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/** FR-2.2: search (name, employee number, email) and filters (department, location, country, level, status). */
public final class EmployeeSpecifications {

	private EmployeeSpecifications() {
	}

	public static Specification<Employee> search(String query) {
		if (query == null || query.isBlank()) {
			return null;
		}
		String pattern = "%" + query.toLowerCase() + "%";
		// workEmail is citext (CitextJdbcType, JDBC type OTHER, not a recognized "string" type to
		// Hibernate's own type-checker) — both cb.like(workEmail, ...) directly ("Operand of
		// 'like' ... is not a string") and cb.lower(workEmail) directly ("Parameter 1 of function
		// 'lower()' has type STRING, but argument ... mapped to 1111") fail Hibernate's SQM
		// argument-type validation, even though the column is text-compatible at the SQL level.
		// An explicit cast to String satisfies the validator by presenting a properly string-typed
		// expression instead of the raw OTHER-typed attribute.
		return (root, cq, cb) -> cb.or(
				cb.like(cb.lower(root.get("firstName")), pattern),
				cb.like(cb.lower(root.get("lastName")), pattern),
				cb.like(cb.lower(root.get("employeeNumber")), pattern),
				cb.like(cb.lower(root.get("workEmail").as(String.class)), pattern));
	}

	public static Specification<Employee> departmentId(UUID departmentId) {
		return equalTo("departmentId", departmentId);
	}

	public static Specification<Employee> locationId(UUID locationId) {
		return equalTo("locationId", locationId);
	}

	public static Specification<Employee> jobLevelId(UUID jobLevelId) {
		return equalTo("jobLevelId", jobLevelId);
	}

	public static Specification<Employee> status(String status) {
		return equalTo("status", status);
	}

	/** {@code IN_BAND}/{@code BELOW_MIN}/{@code ABOVE_MAX}/{@code NO_BAND} — an employee with no
	 * {@code employee_current_comp} row at all (no pay set yet) never matches any value here,
	 * "NO_BAND" included; that band-status is specifically "has a comp record but no band".
	 * Employee has no relationship to EmployeeCurrentComp (see {@code Employee}'s class javadoc
	 * for why) — a correlated subquery, same pattern as {@link #countryCode} below. */
	public static Specification<Employee> bandStatus(String bandStatus) {
		if (bandStatus == null || bandStatus.isBlank()) {
			return null;
		}
		return (root, cq, cb) -> {
			Subquery<UUID> matching = cq.subquery(UUID.class);
			var comp = matching.from(EmployeeCurrentComp.class);
			matching.select(comp.get("employeeId")).where(cb.equal(comp.get("bandStatus"), bandStatus));
			return root.get("id").in(matching);
		};
	}

	/** Employees has no relationship to Location — a subquery on country_code, not a join. */
	public static Specification<Employee> countryCode(String countryCode) {
		if (countryCode == null || countryCode.isBlank()) {
			return null;
		}
		return (root, cq, cb) -> {
			Subquery<UUID> locationsInCountry = cq.subquery(UUID.class);
			var location = locationsInCountry.from(Location.class);
			locationsInCountry.select(location.get("id")).where(cb.equal(location.get("countryCode"), countryCode));
			return root.get("locationId").in(locationsInCountry);
		};
	}

	private static Specification<Employee> equalTo(String field, Object value) {
		if (value == null) {
			return null;
		}
		return (root, cq, cb) -> cb.equal(root.get(field), value);
	}

}
