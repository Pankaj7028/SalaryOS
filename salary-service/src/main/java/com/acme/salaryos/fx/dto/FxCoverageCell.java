package com.acme.salaryos.fx.dto;

import java.time.LocalDate;

/**
 * One cell of the FX coverage matrix (P10.2): whether the (currency, month) pin normalisation
 * would need for this in-use currency actually exists yet. A {@code false} cell is about the
 * <em>future</em> — writes dated that month will 422 until a rate is pinned — never about the
 * past, because every written ledger row already carries its own rate (CLAUDE.md §6.4).
 */
public record FxCoverageCell(LocalDate month, boolean covered) {
}
