package com.acme.salaryos.employee.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Stubs for P4 (Technical-Requirements.md §5). Every method carries the {@code @PreAuthorize} its
 * capability requires per CLAUDE.md §7 — {@code RolePermissionMatrixTest} fails the build on a
 * missing or mismatched one. Bodies return 501 until P4 implements them for real.
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

	/** View employees & their pay: HR Admin, HR Manager, Comp Analyst, Auditor. */
	@GetMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> list() {
		return notImplemented();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> get(@PathVariable UUID id) {
		return notImplemented();
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
