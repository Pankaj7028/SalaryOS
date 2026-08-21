package com.acme.salaryos;

import com.acme.salaryos.reference.domain.Country;
import com.acme.salaryos.reference.domain.Currency;
import com.acme.salaryos.reference.domain.Department;
import com.acme.salaryos.reference.domain.JobFamily;
import com.acme.salaryos.reference.domain.JobLevel;
import com.acme.salaryos.reference.domain.Location;
import com.acme.salaryos.reference.domain.ReasonCode;
import com.acme.salaryos.reference.repository.CountryRepository;
import com.acme.salaryos.reference.repository.CurrencyRepository;
import com.acme.salaryos.reference.repository.DepartmentRepository;
import com.acme.salaryos.reference.repository.JobFamilyRepository;
import com.acme.salaryos.reference.repository.JobLevelRepository;
import com.acme.salaryos.reference.repository.LocationRepository;
import com.acme.salaryos.reference.repository.ReasonCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** P1.9: JPA round-trip for the reference module's entities. */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class ReferenceEntitiesRoundTripTest {

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
	@Autowired
	private CurrencyRepository currencyRepository;
	@Autowired
	private ReasonCodeRepository reasonCodeRepository;

	@Test
	void countryRoundTrips() {
		countryRepository.save(Country.builder().code("DE").name("Germany").defaultCurrency("EUR").build());
		Country loaded = countryRepository.findById("DE").orElseThrow();
		assertThat(loaded.getName()).isEqualTo("Germany");
		assertThat(loaded.getDefaultCurrency()).isEqualTo("EUR");
	}

	@Test
	void locationRoundTrips() {
		countryRepository.save(Country.builder().code("DE").name("Germany").defaultCurrency("EUR").build());
		Location saved = locationRepository.save(Location.builder()
				.countryCode("DE").city("Berlin").name("Berlin HQ").build());
		Location loaded = locationRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getCity()).isEqualTo("Berlin");
		assertThat(loaded.isActive()).isTrue();
	}

	@Test
	void departmentRoundTripsIncludingSelfReferencingParent() {
		Department parent = departmentRepository.save(Department.builder()
				.name("Product & Engineering").code("PENG-RT").build());
		Department child = departmentRepository.save(Department.builder()
				.name("Engineering").code("ENG-RT").parentId(parent.getId()).build());

		Department loaded = departmentRepository.findById(child.getId()).orElseThrow();
		assertThat(loaded.getParentId()).isEqualTo(parent.getId());
	}

	@Test
	void jobFamilyAndJobLevelRoundTrip() {
		JobFamily family = jobFamilyRepository.save(JobFamily.builder().name("Design").code("DESIGN-RT").build());
		JobLevel level = jobLevelRepository.save(JobLevel.builder()
				.jobFamilyId(family.getId()).levelCode("L3").title("Product Designer").sortOrder(3).build());

		JobLevel loaded = jobLevelRepository.findById(level.getId()).orElseThrow();
		assertThat(loaded.getJobFamilyId()).isEqualTo(family.getId());
		assertThat(loaded.getLevelCode()).isEqualTo("L3");
	}

	@Test
	void currencySeedRowsAreReadable() {
		Currency usd = currencyRepository.findById("USD").orElseThrow();
		assertThat(usd.getName()).isEqualTo("US Dollar");
	}

	@Test
	void reasonCodeSeedRowsAreReadable() {
		ReasonCode merit = reasonCodeRepository.findById("MERIT").orElseThrow();
		assertThat(merit.getLabel()).isEqualTo("Merit increase");
	}

}
