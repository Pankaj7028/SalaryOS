package com.acme.salaryos.band.repository;

import com.acme.salaryos.band.domain.SalaryBand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SalaryBandRepository extends JpaRepository<SalaryBand, UUID> {
}
