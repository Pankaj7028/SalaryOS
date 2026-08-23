package com.acme.salaryos.market.repository;

import com.acme.salaryos.market.domain.MarketDataPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Derived queries only — nothing here needs native SQL, so nothing here can forget its schema. */
public interface MarketDataPointRepository extends JpaRepository<MarketDataPoint, UUID> {

	Optional<MarketDataPoint> findBySourceAndJobLevelIdAndCountryCodeAndEffectiveMonth(
			String source, UUID jobLevelId, String countryCode, LocalDate effectiveMonth);

}
