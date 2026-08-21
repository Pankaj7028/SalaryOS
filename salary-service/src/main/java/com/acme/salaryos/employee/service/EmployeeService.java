package com.acme.salaryos.employee.service;

import com.acme.salaryos.common.paging.Cursor;
import com.acme.salaryos.common.paging.CursorCodec;
import com.acme.salaryos.common.paging.KeysetPage;
import com.acme.salaryos.compensation.domain.EmployeeCurrentComp;
import com.acme.salaryos.compensation.repository.EmployeeCurrentCompRepository;
import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.dto.EmployeeDetailResponse;
import com.acme.salaryos.employee.dto.EmployeeSummaryResponse;
import com.acme.salaryos.employee.repository.EmployeeRepository;
import com.acme.salaryos.employee.spec.EmployeeSpecifications;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/** FR-2.2/2.3: search, filters, sort, keyset pagination. */
@Service
public class EmployeeService {

	private static final Sort SORT = Sort.by("lastName", "id");

	private final EmployeeRepository employeeRepository;
	private final EmployeeCurrentCompRepository employeeCurrentCompRepository;
	private final CursorCodec cursorCodec;

	public EmployeeService(
			EmployeeRepository employeeRepository,
			EmployeeCurrentCompRepository employeeCurrentCompRepository,
			CursorCodec cursorCodec) {
		this.employeeRepository = employeeRepository;
		this.employeeCurrentCompRepository = employeeCurrentCompRepository;
		this.cursorCodec = cursorCodec;
	}

	public KeysetPage<EmployeeSummaryResponse> list(
			String query, UUID departmentId, UUID locationId, String countryCode, UUID jobLevelId,
			String status, String cursor, int limit) {

		// Specification.where/and reject a null argument outright (no longer the historical
		// null-means-"no restriction" behaviour) — start unrestricted and fold in only the
		// filters actually supplied.
		Specification<Employee> spec = Specification.<Employee>unrestricted()
				.and(nonNullOrUnrestricted(EmployeeSpecifications.search(query)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.departmentId(departmentId)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.locationId(locationId)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.countryCode(countryCode)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.jobLevelId(jobLevelId)))
				.and(nonNullOrUnrestricted(EmployeeSpecifications.status(status)));

		ScrollPosition position = toScrollPosition(cursor);

		Window<Employee> window = employeeRepository.findBy(spec,
				q -> q.sortBy(SORT).limit(limit).scroll(position));

		List<Employee> employees = window.getContent();
		Map<UUID, EmployeeCurrentComp> currentComp = employeeCurrentCompRepository
				.findAllById(employees.stream().map(Employee::getId).toList())
				.stream()
				.collect(Collectors.toMap(EmployeeCurrentComp::getEmployeeId, c -> c));

		List<EmployeeSummaryResponse> items = employees.stream()
				.map(employee -> toSummary(employee, currentComp.get(employee.getId())))
				.toList();

		String nextCursor = null;
		if (window.hasNext() && !employees.isEmpty()) {
			nextCursor = encodeCursor(window.positionAt(employees.size() - 1));
		}

		return new KeysetPage<>(items, nextCursor);
	}

	public EmployeeDetailResponse get(UUID id) {
		Employee employee = employeeRepository.findById(id).orElseThrow(NoSuchElementException::new);
		EmployeeCurrentComp comp = employeeCurrentCompRepository.findById(id).orElse(null);
		return toDetail(employee, comp);
	}

	private Specification<Employee> nonNullOrUnrestricted(Specification<Employee> spec) {
		return spec == null ? Specification.unrestricted() : spec;
	}

	private ScrollPosition toScrollPosition(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return ScrollPosition.keyset();
		}
		Cursor decoded = cursorCodec.decode(cursor);
		Map<String, Object> keys = Map.of(
				"lastName", decoded.keys().get("lastName"),
				"id", UUID.fromString(decoded.keys().get("id")));
		return ScrollPosition.forward(keys);
	}

	private String encodeCursor(ScrollPosition position) {
		if (!(position instanceof KeysetScrollPosition keysetPosition)) {
			return null;
		}
		Map<String, Object> keys = keysetPosition.getKeys();
		return cursorCodec.encode(new Cursor(Map.of(
				"lastName", String.valueOf(keys.get("lastName")),
				"id", String.valueOf(keys.get("id")))));
	}

	private EmployeeSummaryResponse toSummary(Employee employee, EmployeeCurrentComp comp) {
		return new EmployeeSummaryResponse(
				employee.getId(), employee.getEmployeeNumber(), employee.getFirstName(), employee.getLastName(),
				employee.getWorkEmail(), employee.getDepartmentId(), employee.getLocationId(), employee.getJobLevelId(),
				employee.getEmploymentType(), employee.getFte(), employee.getStatus(), employee.getHireDate(),
				employee.getTerminationDate(),
				comp == null ? null : comp.getBase(),
				comp == null ? null : comp.getCompaRatio(),
				comp == null ? null : comp.getBandStatus());
	}

	private EmployeeDetailResponse toDetail(Employee employee, EmployeeCurrentComp comp) {
		return new EmployeeDetailResponse(
				employee.getId(), employee.getEmployeeNumber(), employee.getFirstName(), employee.getLastName(),
				employee.getWorkEmail(), employee.getDepartmentId(), employee.getLocationId(),
				employee.getJobFamilyId(), employee.getJobLevelId(), employee.getManagerId(),
				employee.getEmploymentType(), employee.getFte(), employee.getStatus(), employee.getHireDate(),
				employee.getTerminationDate(),
				comp == null ? null : comp.getBase(),
				comp == null ? null : comp.getCompaRatio(),
				comp == null ? null : comp.getRangePenetration(),
				comp == null ? null : comp.getBandStatus());
	}

}
