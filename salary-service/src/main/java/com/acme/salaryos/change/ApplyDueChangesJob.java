package com.acme.salaryos.change;

import com.acme.salaryos.change.domain.CompensationChange;
import com.acme.salaryos.change.dto.ApplyDueChangesResult;
import com.acme.salaryos.change.repository.CompensationChangeRepository;
import com.acme.salaryos.change.service.ChangeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * FR-5.7: daily at 02:00 UTC, plus the idempotent manual trigger behind {@code POST
 * /changes/apply-due} — "a scheduled job that silently misses a day is how people get paid the
 * wrong amount" (CLAUDE.md §8). Each due change is applied in {@link ChangeService#applyDueChange}'s
 * own transaction, one per change, so one employee's bad data (a stale FX rate, a missing band)
 * can't block anyone else's raise from landing.
 */
@Slf4j
@Component
public class ApplyDueChangesJob {

	private final CompensationChangeRepository changeRepository;
	private final ChangeService changeService;
	private final Clock clock;

	public ApplyDueChangesJob(CompensationChangeRepository changeRepository, ChangeService changeService, Clock clock) {
		this.changeRepository = changeRepository;
		this.changeService = changeService;
		this.clock = clock;
	}

	@Scheduled(cron = "0 0 2 * * *", zone = "UTC")
	public void runScheduled() {
		ApplyDueChangesResult result = run();
		log.info("ApplyDueChangesJob: {} due, {} applied, {} failed", result.due(), result.applied(), result.failures().size());
	}

	/**
	 * Idempotent: a change moves to {@code APPLIED} as part of applying it, which removes it from
	 * the next call's candidate query — running this twice back to back applies each due change
	 * exactly once, never twice.
	 */
	public ApplyDueChangesResult run() {
		LocalDate today = LocalDate.now(clock);
		List<CompensationChange> due = changeRepository.findByStatusAndEffectiveDateLessThanEqual("APPROVED", today);

		int applied = 0;
		List<ApplyDueChangesResult.Failure> failures = new ArrayList<>();
		for (CompensationChange change : due) {
			try {
				changeService.applyDueChange(change.getId(), today);
				applied++;
			} catch (RuntimeException exception) {
				log.warn("ApplyDueChangesJob: failed to apply change {}", change.getId(), exception);
				failures.add(new ApplyDueChangesResult.Failure(change.getId(), exception.getMessage()));
			}
		}

		return new ApplyDueChangesResult(due.size(), applied, failures);
	}

}
