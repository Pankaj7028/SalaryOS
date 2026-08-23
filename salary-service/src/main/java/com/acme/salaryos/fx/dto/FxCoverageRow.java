package com.acme.salaryos.fx.dto;

import java.time.LocalDate;
import java.util.List;

/** One in-use currency's row of the FX coverage matrix (P10.2): the currency, how many people are
 * paid in it, and each windowed month's coverage. */
public record FxCoverageRow(String currency, long employeeCount, List<FxCoverageCell> cells) {
}
