package com.acme.salaryos.reference.service;

import com.acme.salaryos.reference.dto.CountryResponse;
import com.acme.salaryos.reference.dto.CurrencyResponse;
import com.acme.salaryos.reference.dto.DepartmentResponse;
import com.acme.salaryos.reference.dto.JobFamilyResponse;
import com.acme.salaryos.reference.dto.JobLevelResponse;
import com.acme.salaryos.reference.dto.LocationResponse;
import com.acme.salaryos.reference.repository.CountryRepository;
import com.acme.salaryos.reference.repository.CurrencyRepository;
import com.acme.salaryos.reference.repository.DepartmentRepository;
import com.acme.salaryos.reference.repository.JobFamilyRepository;
import com.acme.salaryos.reference.repository.JobLevelRepository;
import com.acme.salaryos.reference.repository.LocationRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/** Reference lookups that back every screen's filters and dropdowns — small, unpaginated lists. */
@Service
public class ReferenceService {

	private final DepartmentRepository departmentRepository;
	private final LocationRepository locationRepository;
	private final CountryRepository countryRepository;
	private final JobFamilyRepository jobFamilyRepository;
	private final JobLevelRepository jobLevelRepository;
	private final CurrencyRepository currencyRepository;

	public ReferenceService(
			DepartmentRepository departmentRepository, LocationRepository locationRepository,
			CountryRepository countryRepository, JobFamilyRepository jobFamilyRepository,
			JobLevelRepository jobLevelRepository, CurrencyRepository currencyRepository) {
		this.departmentRepository = departmentRepository;
		this.locationRepository = locationRepository;
		this.countryRepository = countryRepository;
		this.jobFamilyRepository = jobFamilyRepository;
		this.jobLevelRepository = jobLevelRepository;
		this.currencyRepository = currencyRepository;
	}

	public List<DepartmentResponse> departments() {
		return departmentRepository.findAll().stream()
				.map(d -> new DepartmentResponse(d.getId(), d.getName(), d.getCode(), d.getParentId()))
				.sorted(Comparator.comparing(DepartmentResponse::name))
				.toList();
	}

	public List<LocationResponse> locations() {
		return locationRepository.findAll().stream()
				.map(l -> new LocationResponse(l.getId(), l.getCountryCode(), l.getCity(), l.getName(), l.isActive()))
				.sorted(Comparator.comparing(LocationResponse::name))
				.toList();
	}

	public List<CountryResponse> countries() {
		return countryRepository.findAll().stream()
				.map(c -> new CountryResponse(c.getCode(), c.getName(), c.getDefaultCurrency()))
				.sorted(Comparator.comparing(CountryResponse::name))
				.toList();
	}

	public List<JobFamilyResponse> jobFamilies() {
		return jobFamilyRepository.findAll().stream()
				.map(f -> new JobFamilyResponse(f.getId(), f.getName(), f.getCode()))
				.sorted(Comparator.comparing(JobFamilyResponse::name))
				.toList();
	}

	public List<JobLevelResponse> jobLevels() {
		return jobLevelRepository.findAll().stream()
				.map(l -> new JobLevelResponse(l.getId(), l.getJobFamilyId(), l.getLevelCode(), l.getTitle(), l.getSortOrder()))
				.sorted(Comparator.comparing(JobLevelResponse::sortOrder))
				.toList();
	}

	public List<CurrencyResponse> currencies() {
		return currencyRepository.findAll().stream()
				.map(c -> new CurrencyResponse(c.getCode(), c.getName()))
				.sorted(Comparator.comparing(CurrencyResponse::code))
				.toList();
	}

}
