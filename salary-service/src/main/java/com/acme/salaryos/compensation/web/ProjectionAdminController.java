package com.acme.salaryos.compensation.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stub for P5.2 (Technical-Requirements.md §5). Re-deriving {@code employee_current_comp} from
 * the ledger is a system-maintenance operation, not a listed §7 capability — treated as
 * admin-only, the same restriction as the other system-level action ("Import / bulk upload").
 */
@RestController
@RequestMapping("/api/admin/rebuild-projection")
public class ProjectionAdminController {

	@PostMapping
	@PreAuthorize("hasRole('HR_ADMIN')")
	public ResponseEntity<Void> rebuild() {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}

}
