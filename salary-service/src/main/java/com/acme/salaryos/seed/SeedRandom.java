package com.acme.salaryos.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One {@link java.util.Random} threaded through every generator (backend doc §9). No
 * {@code UUID.randomUUID()}, no {@code Instant.now()}, no {@code Math.random()} anywhere in
 * {@code seed/} — every id, amount, and date comes from this class, so two runs against empty
 * databases with the same seed produce byte-identical data (P9.2's own Verify clause).
 */
public class SeedRandom {

	/** The seeded universe's "today" — never {@code LocalDate.now()}, which would make a
	 * re-seed produce different relative dates (open periods, hire-date recency) on every run. */
	public static final LocalDate SEED_AS_AT = LocalDate.of(2026, 8, 1);

	private final java.util.Random random;

	public SeedRandom(long seed) {
		this.random = new java.util.Random(seed);
	}

	/** Not a real UUIDv4 (version/variant nibbles aren't forced) — a 128-bit value from the
	 * seeded stream is all Postgres's {@code uuid} column needs, and forcing version bits would
	 * only add code without adding anything the reproducibility guarantee needs. */
	public UUID uuid() {
		return new UUID(random.nextLong(), random.nextLong());
	}

	public int nextInt(int bound) {
		return random.nextInt(bound);
	}

	/** Inclusive of both ends. */
	public int nextInt(int minInclusive, int maxInclusive) {
		return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
	}

	public double nextDouble() {
		return random.nextDouble();
	}

	public boolean chance(double probability) {
		return random.nextDouble() < probability;
	}

	public <T> T pick(List<T> items) {
		return items.get(random.nextInt(items.size()));
	}

	public <T> T pick(T[] items) {
		return items[random.nextInt(items.length)];
	}

	/** Weighted pick — {@code weights} must be the same length as {@code items} and sum to any
	 * positive total (not necessarily 1.0). Used for the pyramid level distribution. */
	public <T> T pickWeighted(List<T> items, double[] weights) {
		double total = 0;
		for (double w : weights) {
			total += w;
		}
		double roll = random.nextDouble() * total;
		double cumulative = 0;
		for (int i = 0; i < items.size(); i++) {
			cumulative += weights[i];
			if (roll < cumulative) {
				return items.get(i);
			}
		}
		return items.get(items.size() - 1);
	}

	/** A log-normal-ish draw centred on {@code mid}, clamped to {@code [min, max]} — most values
	 * land near the middle, with a long-ish tail either side, same shape real comp distributions
	 * actually have (never uniform). */
	public BigDecimal logNormalAround(BigDecimal min, BigDecimal mid, BigDecimal max) {
		double gaussian = random.nextGaussian() * 0.12; // ~12% stdev
		double factor = Math.exp(gaussian);
		BigDecimal value = mid.multiply(BigDecimal.valueOf(factor));
		if (value.compareTo(min) < 0) {
			value = min;
		}
		if (value.compareTo(max) > 0) {
			value = max;
		}
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	/** A random date strictly between {@code from} (inclusive) and {@code to} (exclusive), or
	 * {@code from} itself when the range is empty. */
	public LocalDate dateBetween(LocalDate from, LocalDate to) {
		long fromEpoch = from.toEpochDay();
		long toEpoch = to.toEpochDay();
		if (toEpoch <= fromEpoch) {
			return from;
		}
		long offset = (long) (random.nextDouble() * (toEpoch - fromEpoch));
		return LocalDate.ofEpochDay(fromEpoch + offset);
	}

}
