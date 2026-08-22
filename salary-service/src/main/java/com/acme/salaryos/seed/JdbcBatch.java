package com.acme.salaryos.seed;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** {@code JdbcTemplate.batchUpdate} at 1,000 rows per batch (backend doc §9) — every generator's
 * bulk insert goes through this, so the chunk size is one number to change, not one per file. */
public final class JdbcBatch {

	private static final int CHUNK_SIZE = 1000;

	private JdbcBatch() {
	}

	public static void insert(JdbcTemplate jdbc, String sql, List<Object[]> rows) {
		for (int i = 0; i < rows.size(); i += CHUNK_SIZE) {
			jdbc.batchUpdate(sql, rows.subList(i, Math.min(i + CHUNK_SIZE, rows.size())));
		}
	}

}
