package com.acme.salaryos.change.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * P10.5 / FR-2.2: propose the same uplift for a set of employees selected on the list screen.
 *
 * <p><b>A percentage, not an amount</b> — deliberately, and this is the whole reason the endpoint
 * exists rather than the browser looping {@code POST /api/changes}. Everyone selected is on a
 * different salary, so "give these 40 people a rise" is only expressible per-person as an absolute
 * figure, and computing 40 absolute figures in the browser is exactly the money arithmetic
 * CLAUDE.md §6.1 forbids: a JS {@code number} at 2dp over a 15,2 base is a rounding error per row,
 * landing in a ledger that is insert-only and cannot be quietly corrected.
 *
 * <p>The cap is a guardrail, not a policy: 100% is far past any real merit uplift, and a fat finger
 * that proposes +5000% across a selection is a worse afternoon than one that proposes it for one
 * person. Nothing here approves anything — every row lands as a {@code DRAFT}, so the whole batch
 * is still reviewable and discardable one by one.
 */
public record BulkProposeRequest(
		@NotEmpty @Size(max = 200, message = "Propose for at most 200 employees at a time.") List<UUID> employeeIds,
		@NotNull LocalDate effectiveDate,
		@NotNull
		@DecimalMin(value = "-50.0", message = "A cut deeper than 50% is not a bulk operation.")
		@DecimalMax(value = "100.0", message = "An uplift above 100% is not a bulk operation.")
		BigDecimal percentIncrease,
		@NotBlank String changeReason,
		String note) {
}
