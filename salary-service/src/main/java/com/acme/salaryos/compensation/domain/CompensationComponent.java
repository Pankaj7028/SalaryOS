package com.acme.salaryos.compensation.domain;

import com.acme.salaryos.common.money.Money;
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

import java.math.BigDecimal;
import java.util.UUID;

/** {@code BONUS_TARGET}, {@code HOUSING}, {@code TRANSPORT}, {@code OTHER_ALLOWANCE} (FR-3.4). */
@Entity
@Table(name = "compensation_components")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CompensationComponent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID compensationRecordId;

	private String componentType;

	@Embedded
	private Money amount;

	private BigDecimal percentOfBase;

	@Builder.Default
	private boolean isRecurring = true;

}
