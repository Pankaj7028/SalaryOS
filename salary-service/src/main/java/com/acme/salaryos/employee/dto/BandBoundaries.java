package com.acme.salaryos.employee.dto;

import com.acme.salaryos.common.money.Money;

/** A salary band's min/mid/max, for `<BandBar>` — never shown without the salary it frames. */
public record BandBoundaries(Money min, Money mid, Money max) {
}
