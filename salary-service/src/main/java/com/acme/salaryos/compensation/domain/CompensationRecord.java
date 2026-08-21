package com.acme.salaryos.compensation.domain;

import com.acme.salaryos.common.money.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Insert-only (CLAUDE.md §6.3) — never {@code UPDATE}d. {@code validity} is a DB-generated
 * column backing {@code comp_no_overlap}; deliberately unmapped here, the database owns it.
 * {@code compa_ratio} and {@code band_id} are snapshots taken at write time, not derived on read.
 */
@Entity
@Table(name = "compensation_records")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CompensationRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID employeeId;

	private LocalDate effectiveFrom;

	private LocalDate effectiveTo;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "base_amount")),
			@AttributeOverride(name = "currency", column = @Column(name = "currency"))
	})
	private Money base;

	/** {@code ANNUAL}, {@code MONTHLY}, {@code HOURLY}. */
	private String payFrequency;

	/** {@link #base}'s amount annualised, same currency as {@link #base} — not its own Money. */
	private BigDecimal annualBaseAmount;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "normalized_annual_base")),
			@AttributeOverride(name = "currency", column = @Column(name = "base_currency"))
	})
	private Money normalizedAnnualBase;

	private UUID fxRateId;

	private UUID bandId;

	private BigDecimal compaRatio;

	private BigDecimal rangePenetration;

	private UUID changeId;

	private String changeReason;

	private UUID supersededBy;

	private UUID createdBy;

	@CreationTimestamp
	private Instant createdAt;

}
