package com.acme.salaryos.employee.repository;

import com.acme.salaryos.employee.domain.EmployeeDemographics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmployeeDemographicsRepository extends JpaRepository<EmployeeDemographics, UUID> {
}
