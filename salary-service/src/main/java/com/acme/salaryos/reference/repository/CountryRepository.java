package com.acme.salaryos.reference.repository;

import com.acme.salaryos.reference.domain.Country;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, String> {
}
