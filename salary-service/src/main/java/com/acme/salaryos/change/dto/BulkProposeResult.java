package com.acme.salaryos.change.dto;

import java.util.List;

/**
 * Partial success is the expected outcome, exactly as for the CSV path ({@link
 * ChangeBulkUploadResult}): in any real selection somebody already has an open change, and
 * refusing the whole batch for that would make the feature unusable on precisely the populations
 * it is for. Rows carry their own reason so the screen can say which people were skipped and why.
 */
public record BulkProposeResult(int totalRows, int proposed, int errors, List<ChangeBulkUploadRowResult> rows) {
}
