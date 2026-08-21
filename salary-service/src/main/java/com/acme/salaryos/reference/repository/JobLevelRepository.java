package com.acme.salaryos.reference.repository;

import com.acme.salaryos.reference.domain.JobLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobLevelRepository extends JpaRepository<JobLevel, UUID> {
}
