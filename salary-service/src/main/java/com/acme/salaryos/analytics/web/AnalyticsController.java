package com.acme.salaryos.analytics.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stubs for P7 (Technical-Requirements.md §5); see EmployeeController's class Javadoc. Every
 * method: run insights (aggregate), HR Admin, HR Manager, Comp Analyst. Annotated per-method
 * (not at class level) so every controller in the RBAC guard test scans the same way.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

	@GetMapping("/payroll-cost")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ResponseEntity<Void> payrollCost() {
		return notImplemented();
	}

	@GetMapping("/out-of-band")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ResponseEntity<Void> outOfBand() {
		return notImplemented();
	}

	@GetMapping("/compa-ratio-distribution")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ResponseEntity<Void> compaRatioDistribution() {
		return notImplemented();
	}

	@GetMapping("/pay-gap")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ResponseEntity<Void> payGap() {
		return notImplemented();
	}

	@GetMapping("/increase-cycle")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ResponseEntity<Void> increaseCycle() {
		return notImplemented();
	}

	@GetMapping("/headcount")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ResponseEntity<Void> headcount() {
		return notImplemented();
	}

	private ResponseEntity<Void> notImplemented() {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

}
