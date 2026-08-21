package com.acme.salaryos.analytics.web;

import com.acme.salaryos.analytics.dto.CompaRatioDistributionResponse;
import com.acme.salaryos.analytics.dto.HeadcountResponse;
import com.acme.salaryos.analytics.dto.IncreaseCycleResponse;
import com.acme.salaryos.analytics.dto.OutOfBandResponse;
import com.acme.salaryos.analytics.dto.PayGapResponse;
import com.acme.salaryos.analytics.dto.PayrollCostResponse;
import com.acme.salaryos.analytics.service.AnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Stubs for P7 (Technical-Requirements.md §5); see EmployeeController's class Javadoc. Every
 * method: run insights (aggregate), HR Admin, HR Manager, Comp Analyst. Annotated per-method
 * (not at class level) so every controller in the RBAC guard test scans the same way.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

	private final AnalyticsService analyticsService;

	public AnalyticsController(AnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	@GetMapping("/payroll-cost")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public PayrollCostResponse payrollCost() {
		return analyticsService.payrollCost();
	}

	@GetMapping("/headcount")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public HeadcountResponse headcount() {
		return analyticsService.headcount();
	}

	@GetMapping("/out-of-band")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public OutOfBandResponse outOfBand() {
		return analyticsService.outOfBand();
	}

	@GetMapping("/compa-ratio-distribution")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public CompaRatioDistributionResponse compaRatioDistribution(
			@RequestParam(required = false) UUID departmentId,
			@RequestParam(required = false) UUID jobLevelId,
			@RequestParam(required = false) String countryCode) {
		return analyticsService.compaRatioDistribution(departmentId, jobLevelId, countryCode);
	}

	@GetMapping("/pay-gap")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public PayGapResponse payGap() {
		return analyticsService.payGap();
	}

	@GetMapping("/increase-cycle")
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST')")
	public IncreaseCycleResponse increaseCycle(
			@RequestParam LocalDate fromDate, @RequestParam LocalDate toDate,
			@RequestParam(required = false) BigDecimal budget) {
		return analyticsService.increaseCycle(fromDate, toDate, budget);
	}

}
