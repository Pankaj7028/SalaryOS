package com.acme.salaryos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} — {@code ApplyDueChangesJob} (P6.2) is the first consumer. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
