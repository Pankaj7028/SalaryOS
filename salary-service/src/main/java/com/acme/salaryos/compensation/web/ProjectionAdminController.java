package com.acme.salaryos.compensation.web;

import com.acme.salaryos.compensation.projection.EmployeeCurrentCompProjector;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Re-deriving {@code employee_current_comp} from the ledger (Technical-Requirements.md §4.4/§5) is
 * a system-maintenance operation, not a listed §7 capability — treated as admin-only, the same
 * restriction as the other system-level action ("Import / bulk upload").
 */
@RestController
@RequestMapping("/api/admin/rebuild-projection")
public class ProjectionAdminController {

	private final EmployeeCurrentCompProjector projector;

	public ProjectionAdminController(EmployeeCurrentCompProjector projector) {
		this.projector = projector;
	}

	@PostMapping
	@PreAuthorize("hasRole('HR_ADMIN')")
	public ResponseEntity<Void> rebuild() {
		projector.rebuildAll();
		return ResponseEntity.noContent().build();
	}

}
