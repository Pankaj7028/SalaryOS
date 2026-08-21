package com.acme.salaryos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * One injectable {@link Clock}, system UTC in every real environment. Backend doc §3 rule 6 (no
 * {@code LocalDate.now()} inline) finally has a genuine consumer: {@code ApplyDueChangesJob}
 * (P6.2) needs "today" to decide which approved changes are due, and its own test moves time to
 * prove a change dated tomorrow isn't applied today.
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
