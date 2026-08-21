package com.acme.salaryos.band.domain;

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

/** Effective-dated per (job level × country); never mutated in place (FR-4.5) — see V4. */
@Entity
@Table(name = "salary_bands")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SalaryBand {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID jobLevelId;

	@JdbcTypeCode(SqlTypes.CHAR)
	private String countryCode;

	@JdbcTypeCode(SqlTypes.CHAR)
	private String currency;

	private BigDecimal minAmount;

	private BigDecimal midAmount;

	private BigDecimal maxAmount;

	private LocalDate effectiveFrom;

	private LocalDate effectiveTo;

	private UUID createdBy;

	private String note;

	/** No {@code @Setter}: the only mutation a band permits is closing it when a successor version opens (FR-4.5) — never edited in place otherwise. */
	public void close(LocalDate effectiveTo) {
		this.effectiveTo = effectiveTo;
	}

}
