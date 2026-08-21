package com.acme.salaryos.employee.domain;

import com.acme.salaryos.common.jdbc.CitextJdbcType;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * No JPA relationship to {@code employee_demographics} — that FK points the other way, on
 * purpose, so no fetch here can ever drag a demographic attribute along (CLAUDE.md §6.6).
 */
@Entity
@Table(name = "employees")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Employee {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String employeeNumber;

	private String firstName;

	private String lastName;

	/** {@code citext} in the database. */
	@org.hibernate.annotations.JdbcType(CitextJdbcType.class)
	private String workEmail;

	private UUID departmentId;

	private UUID locationId;

	private UUID jobFamilyId;

	private UUID jobLevelId;

	private UUID managerId;

	private LocalDate hireDate;

	/** {@code FULL_TIME}, {@code PART_TIME}, {@code CONTRACT}. */
	private String employmentType;

	private BigDecimal fte;

	/** {@code ACTIVE}, {@code ON_LEAVE}, {@code TERMINATED}. */
	@Builder.Default
	private String status = "ACTIVE";

	private LocalDate terminationDate;

	@CreationTimestamp
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

}
