package com.acme.salaryos.reference.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stubs for reference lookups (Technical-Requirements.md §5) — departments, locations, countries,
 * job families/levels, currencies. Needed to render nearly every screen's filters and dropdowns,
 * so it's readable by anyone who can see employee/pay data (CLAUDE.md §7's broadest row).
 */
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

	@GetMapping("/departments")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> departments() {
		return notImplemented();
	}

	@GetMapping("/locations")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> locations() {
		return notImplemented();
	}

	@GetMapping("/countries")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> countries() {
		return notImplemented();
	}

	@GetMapping("/job-families")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> jobFamilies() {
		return notImplemented();
	}

	@GetMapping("/job-levels")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> jobLevels() {
		return notImplemented();
	}

	@GetMapping("/currencies")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> currencies() {
		return notImplemented();
	}

	private ResponseEntity<Void> notImplemented() {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

}
