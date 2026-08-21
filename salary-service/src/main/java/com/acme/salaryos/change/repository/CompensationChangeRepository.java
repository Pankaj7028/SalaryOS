package com.acme.salaryos.change.repository;

import com.acme.salaryos.change.domain.CompensationChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompensationChangeRepository extends JpaRepository<CompensationChange, UUID> {
}
