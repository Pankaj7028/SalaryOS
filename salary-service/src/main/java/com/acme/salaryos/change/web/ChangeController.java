package com.acme.salaryos.change.web;

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

/** Stubs for P6 (Technical-Requirements.md §5); see EmployeeController's class Javadoc. */
@RestController
@RequestMapping("/api/changes")
public class ChangeController {

	/** Propose a compensation change: HR Admin, HR Manager, Comp Analyst. */
	@GetMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ResponseEntity<Void> list() {
		return notImplemented();
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ResponseEntity<Void> propose() {
		return notImplemented();
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ResponseEntity<Void> updateDraft(@PathVariable UUID id) {
		return notImplemented();
	}

	@PostMapping("/{id}/submit")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ResponseEntity<Void> submit(@PathVariable UUID id) {
		return notImplemented();
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ResponseEntity<Void> discardDraft(@PathVariable UUID id) {
		return notImplemented();
	}

	/** Approve / reject a change: HR Admin, HR Manager. The proposer can never approve their own (P6.1). */
	@PostMapping("/{id}/approve")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public ResponseEntity<Void> approve(@PathVariable UUID id) {
		return notImplemented();
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public ResponseEntity<Void> reject(@PathVariable UUID id) {
		return notImplemented();
	}

	/**
	 * Applying due changes is a system-level operation on top of approval authority — restricted
	 * to HR Admin, consistent with "Import / bulk upload" being the other admin-only action.
	 */
	@PostMapping("/apply-due")
	@PreAuthorize("hasRole('HR_ADMIN')")
	public ResponseEntity<Void> applyDue() {
		return notImplemented();
	}

	/** Import / bulk upload: HR Admin only. */
	@PostMapping("/bulk-upload")
	@PreAuthorize("hasRole('HR_ADMIN')")
	public ResponseEntity<Void> bulkUpload() {
		return notImplemented();
	}

	private ResponseEntity<Void> notImplemented() {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

}
