package com.acme.salaryos.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Stubs for P8.1 (Technical-Requirements.md §5). Manage users & roles: HR Admin only. */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

	@GetMapping
	@PreAuthorize("hasRole('HR_ADMIN')")
	public ResponseEntity<Void> list() {
		return notImplemented();
	}

	@PostMapping
	@PreAuthorize("hasRole('HR_ADMIN')")
	public ResponseEntity<Void> create() {
		return notImplemented();
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasRole('HR_ADMIN')")
	public ResponseEntity<Void> update(@PathVariable UUID id) {
		return notImplemented();
	}

	@PostMapping("/{id}/reset-token")
	@PreAuthorize("hasRole('HR_ADMIN')")
	public ResponseEntity<Void> issueResetToken(@PathVariable UUID id) {
		return notImplemented();
	}

	private ResponseEntity<Void> notImplemented() {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

}
