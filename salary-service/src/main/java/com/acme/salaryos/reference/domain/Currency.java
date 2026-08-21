package com.acme.salaryos.reference.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Seed-independent reference row backing {@code GET /reference/currencies} (V11). */
@Entity
@Table(name = "currencies")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Currency {

	@Id
	@JdbcTypeCode(SqlTypes.CHAR)
	private String code;

	private String name;

}
