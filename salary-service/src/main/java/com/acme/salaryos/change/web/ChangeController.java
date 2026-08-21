package com.acme.salaryos.change.web;

import com.acme.salaryos.change.ApplyDueChangesJob;
import com.acme.salaryos.change.dto.ApplyDueChangesResult;
import com.acme.salaryos.change.dto.ChangeBulkUploadResult;
import com.acme.salaryos.change.dto.ChangeImpactPreviewResponse;
import com.acme.salaryos.change.dto.ChangeResponse;
import com.acme.salaryos.change.dto.DecisionRequest;
import com.acme.salaryos.change.dto.ProposeChangeRequest;
import com.acme.salaryos.change.dto.UpdateDraftRequest;
import com.acme.salaryos.change.service.ChangeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** P6.1 (Technical-Requirements.md §5, FR-5, CLAUDE.md §8). */
@RestController
@RequestMapping("/api/changes")
public class ChangeController {

	private final ChangeService changeService;
	private final ApplyDueChangesJob applyDueChangesJob;

	public ChangeController(ChangeService changeService, ApplyDueChangesJob applyDueChangesJob) {
		this.changeService = changeService;
		this.applyDueChangesJob = applyDueChangesJob;
	}

	/** Propose a compensation change: HR Admin, HR Manager, Comp Analyst. */
	@GetMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public List<ChangeResponse> list(
			@RequestParam(required = false) UUID employeeId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) LocalDate fromDate,
			@RequestParam(required = false) LocalDate toDate) {
		return changeService.list(employeeId, status, fromDate, toDate);
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ChangeResponse propose(@Valid @RequestBody ProposeChangeRequest request, @AuthenticationPrincipal UUID currentUserId) {
		return changeService.propose(request, currentUserId);
	}

	/** ui doc §8.4: the propose-change dialog's live impact panel — same roles as proposing itself. */
	@GetMapping("/impact-preview")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ChangeImpactPreviewResponse previewImpact(
			@RequestParam UUID employeeId, @RequestParam LocalDate effectiveDate,
			@RequestParam BigDecimal newBaseAmount, @RequestParam String currency) {
		return changeService.previewImpact(employeeId, effectiveDate, newBaseAmount, currency);
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ChangeResponse updateDraft(@PathVariable UUID id, @Valid @RequestBody UpdateDraftRequest request) {
		return changeService.updateDraft(id, request);
	}

	@PostMapping("/{id}/submit")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ChangeResponse submit(@PathVariable UUID id) {
		return changeService.submit(id);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public ResponseEntity<Void> discardDraft(@PathVariable UUID id) {
		changeService.discardDraft(id);
		return ResponseEntity.noContent().build();
	}

	/** Approve / reject a change: HR Admin, HR Manager. The proposer can never approve their own (P6.1). */
	@PostMapping("/{id}/approve")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public ChangeResponse approve(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest request, @AuthenticationPrincipal UUID currentUserId) {
		return changeService.approve(id, currentUserId, request == null ? null : request.decisionNote());
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public ChangeResponse reject(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest request, @AuthenticationPrincipal UUID currentUserId) {
		return changeService.reject(id, currentUserId, request == null ? null : request.decisionNote());
	}

	/**
	 * Applying due changes is a system-level operation on top of approval authority — restricted
	 * to HR Admin, consistent with "Import / bulk upload" being the other admin-only action.
	 */
	@PostMapping("/apply-due")
	@PreAuthorize("hasRole('HR_ADMIN')")
	public ApplyDueChangesResult applyDue() {
		return applyDueChangesJob.run();
	}

	/** FR-5.8: bulk merit cycle upload — HR Admin only, matching CLAUDE.md §7's "Import / bulk upload" row. */
	@PostMapping("/bulk-upload")
	@PreAuthorize("hasRole('HR_ADMIN')")
	public ChangeBulkUploadResult bulkUpload(
			@RequestPart MultipartFile file, @RequestParam LocalDate effectiveDate,
			@AuthenticationPrincipal UUID currentUserId) {
		return changeService.bulkUpload(file, effectiveDate, currentUserId);
	}

}
