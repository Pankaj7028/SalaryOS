package com.acme.salaryos.fx;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FxRateRepository extends JpaRepository<FxRate, UUID> {
}
