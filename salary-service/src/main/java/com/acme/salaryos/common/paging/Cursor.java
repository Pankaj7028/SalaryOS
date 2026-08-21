package com.acme.salaryos.common.paging;

import java.util.Map;

/**
 * An opaque position in a keyset-paginated list: the sort-key values of the last row a client saw.
 * Values are pre-stringified by whoever builds the cursor (e.g. a UUID via {@code toString()}) and
 * re-parsed by whoever consumes it — the codec itself doesn't know the key types.
 */
public record Cursor(Map<String, String> keys) {
}
