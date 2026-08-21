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

	/** FR-2.5: set when job level or location changes without an accompanying pay change (V12). */
	@Builder.Default
	private boolean bandMismatched = false;

	@CreationTimestamp
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	/**
	 * No {@code @Setter}: state changes go through named domain methods (CLAUDE.md §9). Editing
	 * job level or location does not touch pay — it sets {@link #bandMismatched}, cleared only
	 * when a compensation change is applied (P5).
	 */
	public void updateProfile(
			String firstName, String lastName, String workEmail, UUID departmentId, UUID locationId,
			UUID jobFamilyId, UUID jobLevelId, UUID managerId, String employmentType, BigDecimal fte) {
		boolean levelOrLocationChanged = !this.jobLevelId.equals(jobLevelId) || !this.locationId.equals(locationId);

		this.firstName = firstName;
		this.lastName = lastName;
		this.workEmail = workEmail;
		this.departmentId = departmentId;
		this.locationId = locationId;
		this.jobFamilyId = jobFamilyId;
		this.jobLevelId = jobLevelId;
		this.managerId = managerId;
		this.employmentType = employmentType;
		this.fte = fte;

		if (levelOrLocationChanged) {
			this.bandMismatched = true;
		}
	}

	/** FR-2.6: sets status and termination date. Closing the open comp period is the caller's job (needs the ledger). */
	public void terminate(LocalDate terminationDate) {
		this.status = "TERMINATED";
		this.terminationDate = terminationDate;
	}

}
