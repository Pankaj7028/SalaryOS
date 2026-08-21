package com.acme.salaryos.compensation.repository;

import com.acme.salaryos.compensation.domain.CompensationComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompensationComponentRepository extends JpaRepository<CompensationComponent, UUID> {

	List<CompensationComponent> findByCompensationRecordId(UUID compensationRecordId);

}
