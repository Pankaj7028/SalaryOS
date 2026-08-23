package com.acme.salaryos.fx.dto;

import java.util.List;

/** ui doc §8.9 / P8.3 Verify: the trailing window's missing months ride along with the list so the
 * screen can show them without a second round trip. P10.2 adds the in-use coverage matrix. */
public record FxRateAdminResponse(
		List<FxRateResponse> rates, List<MissingFxRateMonth> missing, FxCoverageResponse coverage) {
}
