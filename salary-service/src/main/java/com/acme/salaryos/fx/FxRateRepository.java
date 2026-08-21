package com.acme.salaryos.fx;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface FxRateRepository extends JpaRepository<FxRate, UUID> {

	Optional<FxRate> findByBaseCurrencyAndQuoteCurrencyAndRateMonth(
			String baseCurrency, String quoteCurrency, LocalDate rateMonth);

}
