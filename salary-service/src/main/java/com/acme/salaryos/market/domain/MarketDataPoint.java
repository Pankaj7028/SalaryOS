package com.acme.salaryos.market.domain;

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
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One survey observation: what the market pays for a job level in a country, as at a month.
 *
 * <p>Percentiles stay in the survey's own currency and are never normalised. Normalising a
 * benchmark at import time would bake in one month's FX rate and make the figure move for reasons
 * that have nothing to do with the market — the comparison that matters is band-to-market within a
 * country, and both sides of it are already in the same currency.
 */
@Entity
@Table(name = "market_data_points")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MarketDataPoint {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String source;

	private UUID jobLevelId;

	@JdbcTypeCode(SqlTypes.CHAR)
	private String countryCode;

	@JdbcTypeCode(SqlTypes.CHAR)
	private String currency;

	@Setter
	private BigDecimal p25Amount;

	@Setter
	private BigDecimal p50Amount;

	@Setter
	private BigDecimal p75Amount;

	/** First of month, matching how surveys are published. */
	private LocalDate effectiveMonth;

	@Setter
	private UUID importedBy;

	@CreationTimestamp
	private Instant importedAt;

}
