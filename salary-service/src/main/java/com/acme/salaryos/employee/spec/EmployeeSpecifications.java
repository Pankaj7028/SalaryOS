package com.acme.salaryos.employee.spec;

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
		return (root, cq, cb) -> cb.or(
				cb.like(cb.lower(root.get("firstName")), pattern),
				cb.like(cb.lower(root.get("lastName")), pattern),
				cb.like(cb.lower(root.get("employeeNumber")), pattern),
				cb.like(cb.lower(root.get("workEmail")), pattern));
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
