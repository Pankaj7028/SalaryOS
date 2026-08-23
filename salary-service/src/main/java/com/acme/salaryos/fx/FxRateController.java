package com.acme.salaryos.fx;

import com.acme.salaryos.fx.dto.CreateFxRateRequest;
import com.acme.salaryos.fx.dto.FxRateAdminResponse;
import com.acme.salaryos.fx.dto.FxRateResponse;
import com.acme.salaryos.fx.service.FxRateService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * P8.3 (Technical-Requirements.md §5). Not a listed §7 capability; FX rates are normalisation
 * reference data in the same spirit as salary bands, so mapped the same way: readable by anyone
 * who sees pay data, managed by HR Admin/HR Manager.
 */
@RestController
@RequestMapping("/api/admin/fx-rates")
public class FxRateController {

	private final FxRateService fxRateService;

	public FxRateController(FxRateService fxRateService) {
		this.fxRateService = fxRateService;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','COMP_ANALYST','AUDITOR')")
	public FxRateAdminResponse list() {
		return new FxRateAdminResponse(fxRateService.list(), fxRateService.missingMonths(), fxRateService.coverage());
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
	public FxRateResponse add(@Valid @RequestBody CreateFxRateRequest request, @AuthenticationPrincipal UUID currentUserId) {
		return fxRateService.add(request, currentUserId);
	}

}
