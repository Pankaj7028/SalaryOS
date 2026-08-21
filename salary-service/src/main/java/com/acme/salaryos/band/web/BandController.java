package com.acme.salaryos.band.web;

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

/** Stubs for P5 (Technical-Requirements.md §5); see EmployeeController's class Javadoc. */
@RestController
@RequestMapping("/api/bands")
public class BandController {

	/**
	 * A band is read alongside every salary shown (CLAUDE.md §5.6) — same viewers as pay itself.
	 */
	@GetMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> list() {
		return notImplemented();
	}

	/** Manage salary bands & levels: HR Admin, HR Manager. */
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

	/** Import / bulk upload: HR Admin only. */
	@PostMapping("/import")
	@PreAuthorize("hasRole('HR_ADMIN')")
	public ResponseEntity<Void> importCsv() {
		return notImplemented();
	}

	private ResponseEntity<Void> notImplemented() {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

}
