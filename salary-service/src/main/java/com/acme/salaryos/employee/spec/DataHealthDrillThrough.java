package com.acme.salaryos.employee.spec;

import com.acme.salaryos.compensation.domain.CompensationRecord;
import com.acme.salaryos.compensation.domain.EmployeeCurrentComp;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.reference.domain.Country;
import com.acme.salaryos.reference.domain.Location;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * P11.2: turns one data-health check key into the employee-list filter that shows the people
 * failing it.
 *
 * <p><b>Why this exists rather than more query-string filters.</b> P11.1 gave each check an
 * optional {@code filter} — a query string the list already understands — and two checks got one
 * ({@code bandStatus=NO_BAND}, {@code status=TERMINATED}). The other seven had none, because the
 * conditions they test are not things anyone would ever want as a general-purpose list filter:
 * nobody filters employees by "pay starts before hire date" except when chasing this specific
 * import defect. Adding seven such filters to `/employees` would clutter the filter bar for every
 * user forever to serve one screen. So the drill-through is one parameter, {@code
 * dataHealthCheck}, that names a check rather than describing a condition — and because it resolves
 * to a predicate on the same list endpoint, it inherits that endpoint's RBAC and its audit trail
 * (CLAUDE.md §6.7) for free, which a bespoke "show me the failing rows" endpoint would not.
 *
 * <p><b>Each predicate mirrors its counterpart in {@code DataHealthQuery}.</b> If the two drift,
 * the console says 357 and the drill-through shows 340, which destroys confidence in both numbers.
 * {@code DataHealthDrillThroughTest} reconciles every check's count against its drill-through.
 *
 * <p>One check has no predicate here: {@code circularManagement} needs a recursive CTE, which the
 * Criteria API cannot express at all. It is resolved to ids by {@code DataHealthQuery} instead —
 * safe precisely because a cycle set is tiny by nature, and worth the exception because a cycle is
 * the one defect on the list that makes an org drill-down loop forever.
 */
public final class DataHealthDrillThrough {

	private DataHealthDrillThrough() {
	}

	/** Everything except the terminated, which is the population every check is scoped to. */
	private static Predicate notTerminated(Root<Employee> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
		return cb.notEqual(root.get("status"), "TERMINATED");
	}

	/** Null for an unknown key, so an unrecognised value narrows nothing rather than 500ing. */
	public static Specification<Employee> forCheck(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		return switch (key) {
			case "noCompensation" -> (root, cq, cb) -> {
				Subquery<UUID> withComp = cq.subquery(UUID.class);
				var comp = withComp.from(EmployeeCurrentComp.class);
				withComp.select(comp.get("employeeId"));
				return cb.and(notTerminated(root, cb), cb.not(root.get("id").in(withComp)));
			};
			case "terminatedWithOpenPay" -> (root, cq, cb) -> {
				Subquery<UUID> openPeriod = cq.subquery(UUID.class);
				var record = openPeriod.from(CompensationRecord.class);
				openPeriod.select(record.get("employeeId")).where(cb.isNull(record.get("effectiveTo")));
				return cb.and(cb.equal(root.get("status"), "TERMINATED"), root.get("id").in(openPeriod));
			};
			case "payBeforeHire" -> (root, cq, cb) -> {
				Subquery<UUID> early = cq.subquery(UUID.class);
				var record = early.from(CompensationRecord.class);
				early.select(record.get("employeeId"))
						.where(cb.lessThan(record.get("effectiveFrom"), root.get("hireDate")));
				return root.get("id").in(early);
			};
			case "noBand" -> (root, cq, cb) -> {
				Subquery<UUID> noBand = cq.subquery(UUID.class);
				var comp = noBand.from(EmployeeCurrentComp.class);
				noBand.select(comp.get("employeeId")).where(cb.equal(comp.get("bandStatus"), "NO_BAND"));
				return root.get("id").in(noBand);
			};
			case "bandMismatched" -> (root, cq, cb) ->
					cb.and(notTerminated(root, cb), cb.isTrue(root.get("bandMismatched")));
			case "terminatedManager" -> (root, cq, cb) -> {
				Subquery<UUID> gone = cq.subquery(UUID.class);
				var manager = gone.from(Employee.class);
				gone.select(manager.get("id")).where(cb.equal(manager.get("status"), "TERMINATED"));
				return cb.and(notTerminated(root, cb), root.get("managerId").in(gone));
			};
			case "fullTimePartialFte" -> (root, cq, cb) -> cb.and(
					notTerminated(root, cb),
					cb.equal(root.get("employmentType"), "FULL_TIME"),
					cb.lessThan(root.get("fte"), new BigDecimal("1.00")));
			case "currencyMismatch" -> (root, cq, cb) -> {
				// Employee has no mapped relationship to Location or Country (see Employee's class
				// javadoc), so this is three uncorrelated roots joined in the where clause rather
				// than a path expression -- the same shape EmployeeSpecifications#countryCode uses.
				Subquery<UUID> mismatched = cq.subquery(UUID.class);
				var comp = mismatched.from(EmployeeCurrentComp.class);
				var employee = mismatched.from(Employee.class);
				var location = mismatched.from(Location.class);
				var country = mismatched.from(Country.class);
				mismatched.select(comp.get("employeeId")).where(cb.and(
						cb.equal(employee.get("id"), comp.get("employeeId")),
						cb.equal(location.get("id"), employee.get("locationId")),
						cb.equal(country.get("code"), location.get("countryCode")),
						cb.notEqual(comp.get("base").get("currency"), country.get("defaultCurrency"))));
				return root.get("id").in(mismatched);
			};
			default -> null;
		};
	}

}
