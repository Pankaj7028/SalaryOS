package com.acme.salaryos.change.dto;

import com.acme.salaryos.common.money.Money;
import com.acme.salaryos.employee.dto.BandBoundaries;

import java.math.BigDecimal;

/**
 * ui doc §8.4's live impact panel — every figure computed server-side (CLAUDE.md §6.1), the same
 * math {@link com.acme.salaryos.compensation.effective.EffectiveDating#apply} would use if this
 * proposal were actually submitted and applied. Nothing here is persisted.
 */
public record ChangeImpactPreviewResponse(
		Money currentBase,
		Money proposedBase,
		Money deltaAmount,
		BigDecimal deltaPercent,
		BigDecimal currentCompaRatio,
		BigDecimal proposedCompaRatio,
		BigDecimal currentRangePenetration,
		BigDecimal proposedRangePenetration,
		String currentBandStatus,
		String proposedBandStatus,
		BandBoundaries band,
		boolean noteRequired,
		int peerCohortSize,
		boolean peerSuppressed,
		Integer peerPercentileBefore,
		Integer peerPercentileAfter) {
}
