package com.acme.salaryos.reference.repository;

import com.acme.salaryos.reference.domain.JobFamily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobFamilyRepository extends JpaRepository<JobFamily, UUID> {
}
