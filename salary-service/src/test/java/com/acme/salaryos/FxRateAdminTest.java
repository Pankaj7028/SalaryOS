package com.acme.salaryos;

import com.acme.salaryos.auth.domain.User;
import com.acme.salaryos.auth.repository.UserRepository;
import com.acme.salaryos.fx.dto.CreateFxRateRequest;
import com.acme.salaryos.fx.dto.FxRateResponse;
import com.acme.salaryos.fx.dto.MissingFxRateMonth;
import com.acme.salaryos.fx.service.FxRateAlreadyExistsException;
import com.acme.salaryos.fx.service.FxRateService;
import com.acme.salaryos.reference.domain.Country;
import com.acme.salaryos.reference.repository.CountryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P8.3's own Verify clause: a missing rate month is visible and addable. {@code app.base-currency}
 * is {@code USD} in every environment (no test override) — a country whose default currency is
 * neither {@code USD} nor used by any other fixture proves the "missing" computation for real,
 * not against a currency some other test class's fixtures might already have pinned a rate for.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=",
		"spring.flyway.schemas=salary_schema",
		"spring.flyway.default-schema=salary_schema",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.default_schema=salary_schema"
})
@Import(TestcontainersConfiguration.class)
class FxRateAdminTest {

	@Autowired
	private FxRateService fxRateService;
	@Autowired
	private CountryRepository countryRepository;
	@Autowired
	private UserRepository userRepository;

	@Test
	void theCurrentMonthIsMissingUntilAnAdminAddsItThenItMovesFromMissingToTheRateList() {
		countryRepository.findById("ZX").orElseGet(() -> countryRepository.save(
				Country.builder().code("ZX").name("Fx Test Land").defaultCurrency("ZZQ").build()));
		User admin = userRepository.save(User.builder()
				.email("fx-admin@acme.test").fullName("Fx Admin").passwordHash("{argon2}stub").role("HR_ADMIN").build());

		LocalDate currentMonth = YearMonth.now().atDay(1);

		// Before any rate is added, the current month is visible in the missing list.
		List<MissingFxRateMonth> missingBefore = fxRateService.missingMonths();
		assertThat(missingBefore).anyMatch(m -> m.baseCurrency().equals("ZZQ") && m.rateMonth().equals(currentMonth) && m.quoteCurrency().equals("USD"));

		// It's addable: POSTing the rate succeeds and returns the normalised (first-of-month) row.
		FxRateResponse added = fxRateService.add(new CreateFxRateRequest(currentMonth, "ZZQ", "USD", new BigDecimal("1.2345")), admin.getId());
		assertThat(added.rateMonth()).isEqualTo(currentMonth);
		assertThat(added.rate()).isEqualByComparingTo("1.2345");

		// Now it has moved out of "missing" and into the rate list.
		List<MissingFxRateMonth> missingAfter = fxRateService.missingMonths();
		assertThat(missingAfter).noneMatch(m -> m.baseCurrency().equals("ZZQ") && m.rateMonth().equals(currentMonth));
		assertThat(fxRateService.list()).anyMatch(r -> r.baseCurrency().equals("ZZQ") && r.rateMonth().equals(currentMonth));

		// Adding the identical (month, base, quote) again is refused, not silently overwritten —
		// a pinned rate a comp record may already reference must never change under it (CLAUDE.md §6.4).
		assertThatThrownBy(() -> fxRateService.add(new CreateFxRateRequest(currentMonth, "ZZQ", "USD", new BigDecimal("9.9")), admin.getId()))
				.isInstanceOf(FxRateAlreadyExistsException.class);
	}

}
