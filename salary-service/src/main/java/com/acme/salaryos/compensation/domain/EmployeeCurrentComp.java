package com.acme.salaryos.compensation.domain;

import com.acme.salaryos.common.money.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The current-pay projection (Technical-Requirements.md §4.4) — a cache maintained
 * transactionally by the service, not a trigger. The ledger ({@link CompensationRecord}) is the
 * truth; {@code ProjectionConsistencyTest} (P5.2) re-derives this and asserts equality.
 */
@Entity
@Table(name = "employee_current_comp")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EmployeeCurrentComp {

	@Id
	private UUID employeeId;

	private UUID compensationRecordId;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "amount", column = @Column(name = "base_amount")),
			@AttributeOverride(name = "currency", column = @Column(name = "currency"))
	})
	private Money base;

	private BigDecimal annualBaseAmount;

	/** Always in {@code APP_BASE_CURRENCY} (USD) — this table has no column to store it otherwise. */
	private BigDecimal normalizedAnnualBase;

	private UUID bandId;

	private BigDecimal compaRatio;

	private BigDecimal rangePenetration;

	/** {@code IN_BAND}, {@code BELOW_MIN}, {@code ABOVE_MAX}, {@code NO_BAND}. */
	private String bandStatus;

	@UpdateTimestamp
	private Instant refreshedAt;

}
