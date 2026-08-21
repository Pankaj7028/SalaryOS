package com.acme.salaryos.fx;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One pinned rate per (month, base, quote). Not a {@code Money} value — an exchange rate, scale
 * 8, not a currency amount at scale 2.
 */
@Entity
@Table(name = "fx_rates")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FxRate {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** First of month. */
	private LocalDate rateMonth;

	@JdbcTypeCode(SqlTypes.CHAR)
	private String baseCurrency;

	@JdbcTypeCode(SqlTypes.CHAR)
	private String quoteCurrency;

	private BigDecimal rate;

}
