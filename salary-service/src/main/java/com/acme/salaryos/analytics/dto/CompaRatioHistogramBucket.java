package com.acme.salaryos.analytics.dto;

/** FR-6.3: one fixed compa-ratio band — "&lt;0.80", "0.80–0.90", … "≥1.20" — and how many active
 * employees with a band fall in it. Bucket boundaries are a product constant, not a request
 * parameter (comparable across every filter combination). */
public record CompaRatioHistogramBucket(String bucket, int count) {
}
