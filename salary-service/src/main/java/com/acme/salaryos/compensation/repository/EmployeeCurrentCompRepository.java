package com.acme.salaryos.compensation.repository;

import com.acme.salaryos.compensation.domain.EmployeeCurrentComp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmployeeCurrentCompRepository extends JpaRepository<EmployeeCurrentComp, UUID> {
}
