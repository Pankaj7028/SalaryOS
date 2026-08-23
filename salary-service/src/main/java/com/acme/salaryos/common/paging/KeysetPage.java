package com.acme.salaryos.common.paging;

import java.util.List;

/**
 * A page of a keyset-paginated list. {@code nextCursor} is null exactly when this is the last page.
 *
 * <p>{@code totalCount} is how many rows the filter matches in total, not how many are on this page
 * (FR-2.2, P10.5). It is what turns "25 rows" into "25 of 412" — the difference between a screen
 * that shows you an answer and one that shows you a fragment of an answer you cannot size. It also
 * makes a page jump possible at all: without a total there is no last page to jump to.
 *
 * <p><b>The count must be produced by the same query path that produced {@code items}.</b> The two
 * employee-list sorts do not select over the same population — the compa-ratio sort inner-joins
 * {@code employee_current_comp}, so a day-one hire with no pay set yet is not in its ordering at
 * all, while the last-name sort lists them. On the seed that is a 420-row difference. A count taken
 * from the wrong path would be off by exactly that, silently, and only for some filters.
 */
public record KeysetPage<T>(List<T> items, String nextCursor, long totalCount) {

	public boolean hasMore() {
		return nextCursor != null;
	}

}
