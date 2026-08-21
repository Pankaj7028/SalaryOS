package com.acme.salaryos.compensation.repository;

import com.acme.salaryos.compensation.domain.EmployeeCurrentComp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmployeeCurrentCompRepository extends JpaRepository<EmployeeCurrentComp, UUID> {

	/** Bands grid's per-cell headcount (ui doc §8.6). */
	long countByBandId(UUID bandId);

	/** The cohort a band-version "how many employees change status" preview evaluates (ui doc §8.6). */
	List<EmployeeCurrentComp> findByBandId(UUID bandId);

}
