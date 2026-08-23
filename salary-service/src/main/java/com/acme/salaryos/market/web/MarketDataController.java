package com.acme.salaryos.market.web;

import com.acme.salaryos.market.dto.MarketImportResult;
import com.acme.salaryos.market.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * P11.5. HR_ADMIN only — CLAUDE.md §7's RBAC table has one "Import / bulk upload" row and it
 * grants HR Admin alone, covering every CSV type rather than one capability per file format.
 */
@Slf4j
@RestController
@RequestMapping("/api/market-data")
@RequiredArgsConstructor
public class MarketDataController {

	private final MarketDataService marketDataService;

	@PostMapping("/import")
	@PreAuthorize("hasRole('HR_ADMIN')")
	public MarketImportResult importCsv(
			@RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "true") boolean dryRun,
			@AuthenticationPrincipal UUID currentUserId) {
		return marketDataService.importCsv(file, dryRun, currentUserId);
	}

}
