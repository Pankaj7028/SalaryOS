package com.acme.salaryos.change.dto;

import java.util.List;

/** FR-5.8: partial success is the expected outcome — a merit cycle CSV proposes what it can and reports the rest as counts. */
public record ChangeBulkUploadResult(int totalRows, int proposed, int errors, List<ChangeBulkUploadRowResult> rows) {
}
