package com.acme.salaryos.common.paging;

import java.util.List;

/** A page of a keyset-paginated list. {@code nextCursor} is null exactly when this is the last page. */
public record KeysetPage<T>(List<T> items, String nextCursor) {

	public boolean hasMore() {
		return nextCursor != null;
	}

}
