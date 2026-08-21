package com.acme.salaryos.change.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The proposal/approval lifecycle (CLAUDE.md §8): {@code DRAFT -> PENDING -> APPROVED -> APPLIED},
 * with {@code REJECTED}. Only {@code APPLIED} ever produces a {@link
 * com.acme.salaryos.compensation.domain.CompensationRecord}. {@code currentBaseAmount} and
 * {@code newBaseAmount} share one {@code currency} column — not two separate {@code Money}
 * embeds.
 */
@Entity
@Table(name = "compensation_changes")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CompensationChange {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID employeeId;

	/** {@code DRAFT}, {@code PENDING}, {@code APPROVED}, {@code APPLIED}, {@code REJECTED}. */
	private String status;

	private LocalDate effectiveDate;

	private BigDecimal currentBaseAmount;

	private BigDecimal newBaseAmount;

	@JdbcTypeCode(SqlTypes.CHAR)
	private String currency;

	private String changeReason;

	private String performanceRating;

	private String note;

	private UUID proposedBy;

	@CreationTimestamp
	private Instant proposedAt;

	private UUID decidedBy;

	private Instant decidedAt;

	private String decisionNote;

	private Instant appliedAt;

	private UUID appliedRecordId;

}
