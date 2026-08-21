package com.acme.salaryos.reference.repository;

import com.acme.salaryos.reference.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
}
