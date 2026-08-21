package com.acme.salaryos.compensation.repository;

import com.acme.salaryos.compensation.domain.CompensationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompensationRecordRepository extends JpaRepository<CompensationRecord, UUID> {

	Optional<CompensationRecord> findByEmployeeIdAndEffectiveToIsNull(UUID employeeId);

	/** Every employee's currently-open period at once — at most one per employee (comp_no_overlap makes two impossible). Used to rebuild the whole {@code employee_current_comp} projection from the ledger. */
	List<CompensationRecord> findByEffectiveToIsNull();

	/** FR-6.7: the full ledger for one employee, newest period first. */
	List<CompensationRecord> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);

	/** FR-3.6: the one period valid on {@code asAt} — "what was this person paid on 2024-06-30". */
	@Query("SELECT r FROM CompensationRecord r WHERE r.employeeId = :employeeId "
			+ "AND r.effectiveFrom <= :asAt AND (r.effectiveTo IS NULL OR r.effectiveTo > :asAt)")
	Optional<CompensationRecord> findAsAt(@Param("employeeId") UUID employeeId, @Param("asAt") LocalDate asAt);

}
