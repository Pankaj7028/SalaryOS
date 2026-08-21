package com.acme.salaryos.change.repository;

import com.acme.salaryos.change.domain.CompensationChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompensationChangeRepository
		extends JpaRepository<CompensationChange, UUID>, JpaSpecificationExecutor<CompensationChange> {

	/** FR-5.6: at most one per employee — backed by V7's partial unique index (the real guarantee; this is the proactive, friendly-error-producing check). */
	Optional<CompensationChange> findByEmployeeIdAndStatusIn(UUID employeeId, List<String> statuses);

	/** FR-5.7: every approved change whose effective date has arrived — the candidate set {@code ApplyDueChangesJob} works through. */
	List<CompensationChange> findByStatusAndEffectiveDateLessThanEqual(String status, LocalDate asOf);

}
