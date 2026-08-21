package com.acme.salaryos.audit;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stub for P8.3 (Technical-Requirements.md §5). Read the audit log: HR Admin, Auditor. */
@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {

	@GetMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','AUDITOR')")
	public ResponseEntity<Void> search() {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

}
