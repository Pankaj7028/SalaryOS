package com.acme.salaryos.compensation.repository;

import com.acme.salaryos.compensation.domain.CompensationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompensationRecordRepository extends JpaRepository<CompensationRecord, UUID> {

	Optional<CompensationRecord> findByEmployeeIdAndEffectiveToIsNull(UUID employeeId);

}
