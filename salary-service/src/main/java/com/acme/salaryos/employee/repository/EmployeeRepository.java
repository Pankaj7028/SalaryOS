package com.acme.salaryos.employee.repository;

import com.acme.salaryos.employee.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {

	/** FR-6.6's cohort: same job level, any location in the peer's country, excluding a status (TERMINATED). */
	List<Employee> findByJobLevelIdAndLocationIdInAndStatusNot(UUID jobLevelId, List<UUID> locationIds, String excludedStatus);

	/** FR-5.8: bulk merit upload rows key an employee by their number, not their UUID — this is a human-edited CSV. */
	Optional<Employee> findByEmployeeNumber(String employeeNumber);

}
