package com.acme.salaryos.compensation.repository;

import com.acme.salaryos.compensation.domain.CompensationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompensationRecordRepository extends JpaRepository<CompensationRecord, UUID> {

	Optional<CompensationRecord> findByEmployeeIdAndEffectiveToIsNull(UUID employeeId);

	/** Every employee's currently-open period at once — at most one per employee (comp_no_overlap makes two impossible). Used to rebuild the whole {@code employee_current_comp} projection from the ledger. */
	List<CompensationRecord> findByEffectiveToIsNull();

}
