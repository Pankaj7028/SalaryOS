package com.acme.salaryos.common.money;

import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A money value never travels without its currency (CLAUDE.md §6.2). {@code BigDecimal} only,
 * scale 2, HALF_UP — never {@code double}, never a JS number doing arithmetic.
 */
@Embeddable
public record Money(BigDecimal amount, @JdbcTypeCode(SqlTypes.CHAR) String currency) {

	public Money {
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(currency, "currency");
		amount = amount.setScale(2, RoundingMode.HALF_UP);
	}

}
