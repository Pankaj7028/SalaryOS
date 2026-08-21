package com.acme.salaryos.reference.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Seed-independent reference row: the FR-5.2 change-reason vocabulary plus INITIAL (V11). */
@Entity
@Table(name = "reason_codes")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReasonCode {

	@Id
	private String code;

	private String label;

}
