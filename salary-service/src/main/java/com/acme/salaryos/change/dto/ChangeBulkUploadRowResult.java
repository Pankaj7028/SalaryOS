package com.acme.salaryos.change.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code action} is {@code PROPOSED} (a DRAFT change was created) or {@code ERROR} (row rejected, nothing created) — the full set is the "downloadable error report," same shape as {@code BandImportRowResult}. */
public record ChangeBulkUploadRowResult(
		int rowNumber,
		String action,
		String employeeNumber,
		BigDecimal newAmount,
		String changeReason,
		UUID changeId,
		String error) {
}
