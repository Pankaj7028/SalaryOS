package com.acme.salaryos.band.web;

import com.acme.salaryos.band.dto.BandImportResult;
import com.acme.salaryos.band.dto.BandResponse;
import com.acme.salaryos.band.dto.BandVersionImpactResponse;
import com.acme.salaryos.band.dto.CreateBandRequest;
import com.acme.salaryos.band.dto.UpdateBandRequest;
import com.acme.salaryos.band.service.BandService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import java.util.List;
import java.util.UUID;

/** P5.3 (Technical-Requirements.md §5, FR-4.5/FR-4.6). */
@RestController
@RequestMapping("/api/bands")
public class BandController {

	private final BandService bandService;

	public BandController(BandService bandService) {
		this.bandService = bandService;
	}

	/**
	 * A band is read alongside every salary shown (CLAUDE.md §5.6) — same viewers as pay itself.
	 */
	@GetMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public List<BandResponse> list() {
		return bandService.list();
	}

	/** Manage salary bands & levels: HR Admin, HR Manager. */
	@PostMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public BandResponse create(@Valid @RequestBody CreateBandRequest request, @AuthenticationPrincipal UUID currentUserId) {
		return bandService.create(request, currentUserId);
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public BandResponse update(
			@PathVariable UUID id, @Valid @RequestBody UpdateBandRequest request, @AuthenticationPrincipal UUID currentUserId) {
		return bandService.update(id, request, currentUserId);
	}

	/** ui doc §8.6: the status-change count shown before saving a new version. Same viewers as editing itself. */
	@GetMapping("/{id}/preview-version-impact")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public BandVersionImpactResponse previewVersionImpact(
			@PathVariable UUID id, @RequestParam BigDecimal minAmount, @RequestParam BigDecimal midAmount,
			@RequestParam BigDecimal maxAmount) {
		return bandService.previewVersionImpact(id, minAmount, midAmount, maxAmount);
	}

	/** Import / bulk upload: HR Admin only. */
	@PostMapping("/import")
	@PreAuthorize("hasRole('HR_ADMIN')")
	public BandImportResult importCsv(
			@RequestPart MultipartFile file, @RequestParam(defaultValue = "false") boolean dryRun,
			@AuthenticationPrincipal UUID currentUserId) {
		return bandService.importCsv(file, dryRun, currentUserId);
	}

}
