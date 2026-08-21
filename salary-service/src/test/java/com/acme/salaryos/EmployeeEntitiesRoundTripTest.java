package com.acme.salaryos;

import com.acme.salaryos.employee.domain.Employee;
import com.acme.salaryos.employee.domain.EmployeeDemographics;
import com.acme.salaryos.employee.repository.EmployeeDemographicsRepository;
import com.acme.salaryos.employee.repository.EmployeeRepository;
import com.acme.salaryos.reference.domain.Country;
import com.acme.salaryos.reference.domain.Department;
import com.acme.salaryos.reference.domain.JobFamily;
import com.acme.salaryos.reference.domain.JobLevel;
import com.acme.salaryos.reference.domain.Location;
import com.acme.salaryos.reference.repository.CountryRepository;
import com.acme.salaryos.reference.repository.DepartmentRepository;
import com.acme.salaryos.reference.repository.JobFamilyRepository;
import com.acme.salaryos.reference.repository.JobLevelRepository;
import com.acme.salaryos.reference.repository.LocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** P1.9: JPA round-trip for Employee and EmployeeDemographics. */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class EmployeeEntitiesRoundTripTest {

	@Autowired
	private EmployeeRepository employeeRepository;
	@Autowired
	private EmployeeDemographicsRepository employeeDemographicsRepository;
	@Autowired
	private CountryRepository countryRepository;
	@Autowired
	private LocationRepository locationRepository;
	@Autowired
	private DepartmentRepository departmentRepository;
	@Autowired
	private JobFamilyRepository jobFamilyRepository;
	@Autowired
	private JobLevelRepository jobLevelRepository;

	@Test
	void employeeRoundTrips() {
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("Austin HQ JPA").build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering").code("ENG-DEPT-JPA").build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("ENG-JPA").build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L5").title("Staff Engineer").sortOrder(5).build());

		Employee saved = employeeRepository.save(Employee.builder()
				.employeeNumber("E-JPA-0001")
				.firstName("Katherine")
				.lastName("Johnson")
				.workEmail("katherine.johnson-jpa@acme.test")
				.departmentId(department.getId())
				.locationId(location.getId())
				.jobFamilyId(family.getId())
				.jobLevelId(level.getId())
				.hireDate(LocalDate.of(2020, 1, 15))
				.employmentType("FULL_TIME")
				.fte(BigDecimal.ONE)
				.build());

		Employee loaded = employeeRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getEmployeeNumber()).isEqualTo("E-JPA-0001");
		assertThat(loaded.getStatus()).isEqualTo("ACTIVE");
		assertThat(loaded.getFte()).isEqualByComparingTo("1.00");
		assertThat(loaded.getCreatedAt()).isNotNull();
	}

	@Test
	void employeeDemographicsRoundTripsAndStaysUnlinkedFromEmployeeEntity() {
		countryRepository.save(Country.builder().code("US").name("United States").defaultCurrency("USD").build());
		Location location = locationRepository.save(Location.builder()
				.countryCode("US").city("Austin").name("Austin HQ JPA Demo").build());
		Department department = departmentRepository.save(Department.builder()
				.name("Engineering").code("ENG-DEPT-JPA-DEMO").build());
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Engineering").code("ENG-JPA-DEMO").build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L2").title("Engineer II").sortOrder(2).build());

		Employee employee = employeeRepository.save(Employee.builder()
				.employeeNumber("E-JPA-0002")
				.firstName("Dorothy")
				.lastName("Vaughan")
				.workEmail("dorothy.vaughan-jpa@acme.test")
				.departmentId(department.getId())
				.locationId(location.getId())
				.jobFamilyId(family.getId())
				.jobLevelId(level.getId())
				.hireDate(LocalDate.of(2021, 3, 1))
				.employmentType("FULL_TIME")
				.fte(BigDecimal.ONE)
				.build());

		employeeDemographicsRepository.save(EmployeeDemographics.builder()
				.employeeId(employee.getId())
				.gender("F")
				.dateOfBirth(LocalDate.of(1990, 5, 20))
				.build());

		EmployeeDemographics loaded = employeeDemographicsRepository.findById(employee.getId()).orElseThrow();
		assertThat(loaded.getGender()).isEqualTo("F");

		// The Employee entity itself has no field or relationship that can reach demographics.
		assertThat(java.util.Arrays.stream(Employee.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
				.noneMatch(name -> name.toLowerCase().contains("demographic"));
	}

}
