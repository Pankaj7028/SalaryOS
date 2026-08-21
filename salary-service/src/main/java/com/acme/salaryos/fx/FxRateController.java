package com.acme.salaryos.fx;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stub for P8.3 (Technical-Requirements.md §5). Not a listed §7 capability; FX rates are
 * normalisation reference data in the same spirit as salary bands, so mapped the same way:
 * readable by anyone who sees pay data, managed by HR Admin/HR Manager.
 */
@RestController
@RequestMapping("/api/admin/fx-rates")
public class FxRateController {

	@GetMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public ResponseEntity<Void> list() {
		return notImplemented();
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public ResponseEntity<Void> add() {
		return notImplemented();
	}

	private ResponseEntity<Void> notImplemented() {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

}
