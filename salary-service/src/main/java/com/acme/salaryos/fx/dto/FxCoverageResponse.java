package com.acme.salaryos.fx.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The P10.2 coverage matrix: currency × month over the currencies actually paid in
 * ({@code employee_current_comp}), same trailing-plus-lookahead window as {@link MissingFxRateMonth}.
 *
 * @param months        ascending month starts covered by every row
 * @param quoteCurrency the normalisation target every rate converts to — echoed so a client can
 *                      prefill an add-rate action without hardcoding configuration
 * @param rows          one per currency in use; a currency with no employees never appears here
 */
public record FxCoverageResponse(List<LocalDate> months, String quoteCurrency, List<FxCoverageRow> rows) {
}
