package com.acme.salaryos.fx.dto;

import java.util.List;

/** ui doc §8.9 / P8.3 Verify: the trailing window's missing months ride along with the list so the
 * screen can show them without a second round trip. */
public record FxRateAdminResponse(List<FxRateResponse> rates, List<MissingFxRateMonth> missing) {
}
