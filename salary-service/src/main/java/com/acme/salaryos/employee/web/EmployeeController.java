package com.acme.salaryos.employee.web;

import com.acme.salaryos.common.paging.KeysetPage;
import com.acme.salaryos.employee.dto.EmployeeDetailResponse;
import com.acme.salaryos.employee.dto.EmployeeSummaryResponse;
import com.acme.salaryos.employee.service.EmployeeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * P4 (Technical-Requirements.md §5). {@code list}/{@code get} are real (P4.1); the rest are still
 * stubs (P4.2+). Every method carries the {@code @PreAuthorize} its capability requires per
 * CLAUDE.md §7 — {@code RolePermissionMatrixTest} fails the build on a missing or mismatched one.
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

	private final EmployeeService employeeService;

	public EmployeeController(EmployeeService employeeService) {
		this.employeeService = employeeService;
	}

	/** View employees & their pay: HR Admin, HR Manager, Comp Analyst, Auditor. */
	@GetMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public KeysetPage<EmployeeSummaryResponse> list(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) UUID departmentId,
			@RequestParam(required = false) UUID locationId,
			@RequestParam(required = false) String countryCode,
			@RequestParam(required = false) UUID jobLevelId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "50") int limit) {
		int pageSize = Math.min(Math.max(limit, 1), 200);
		return employeeService.list(q, departmentId, locationId, countryCode, jobLevelId, status, cursor, pageSize);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public EmployeeDetailResponse get(@PathVariable UUID id) {
		return employeeService.get(id);
	}

	@GetMapping("/{id}/compensation")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> compensationHistory(@PathVariable UUID id) {
		return notImplemented();
	}

	@GetMapping("/{id}/compensation/as-at")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> compensationAsAt(@PathVariable UUID id) {
		return notImplemented();
	}

	@GetMapping("/{id}/peers")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> peers(@PathVariable UUID id) {
		return notImplemented();
	}

	@GetMapping("/export")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> export() {
		return notImplemented();
	}

	/** Create / edit employee record: HR Admin, HR Manager. */
	@PostMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public ResponseEntity<Void> create() {
		return notImplemented();
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public ResponseEntity<Void> update(@PathVariable UUID id) {
		return notImplemented();
	}

	@PostMapping("/{id}/terminate")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public ResponseEntity<Void> terminate(@PathVariable UUID id) {
		return notImplemented();
	}

	private ResponseEntity<Void> notImplemented() {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

}
