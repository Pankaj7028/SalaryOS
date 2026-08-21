package com.acme.salaryos.reference.web;

import com.acme.salaryos.reference.dto.CountryResponse;
import com.acme.salaryos.reference.dto.CurrencyResponse;
import com.acme.salaryos.reference.dto.DepartmentResponse;
import com.acme.salaryos.reference.dto.JobFamilyResponse;
import com.acme.salaryos.reference.dto.JobLevelResponse;
import com.acme.salaryos.reference.dto.LocationResponse;
import com.acme.salaryos.reference.service.ReferenceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reference lookups (Technical-Requirements.md §5) — departments, locations, countries, job
 * families/levels, currencies. Needed to render nearly every screen's filters and dropdowns, so
 * it's readable by anyone who can see employee/pay data (CLAUDE.md §7's broadest row).
 */
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

	private final ReferenceService referenceService;

	public ReferenceController(ReferenceService referenceService) {
		this.referenceService = referenceService;
	}

	@GetMapping("/departments")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public List<DepartmentResponse> departments() {
		return referenceService.departments();
	}

	@GetMapping("/locations")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public List<LocationResponse> locations() {
		return referenceService.locations();
	}

	@GetMapping("/countries")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public List<CountryResponse> countries() {
		return referenceService.countries();
	}

	@GetMapping("/job-families")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public List<JobFamilyResponse> jobFamilies() {
		return referenceService.jobFamilies();
	}

	@GetMapping("/job-levels")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public List<JobLevelResponse> jobLevels() {
		return referenceService.jobLevels();
	}

	@GetMapping("/currencies")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public List<CurrencyResponse> currencies() {
		return referenceService.currencies();
	}

}
