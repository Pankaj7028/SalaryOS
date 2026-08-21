package com.acme.salaryos.band.dto;

/**
 * ui doc §8.6: "Creating a new version shows how many employees change status as a result before
 * saving — the most useful number on the screen." Purely advisory — versioning a band never
 * rewrites any employee's stored compa-ratio/status; those stay frozen until their next
 * compensation change is applied (backend doc §2.3: a snapshot, never recomputed on read).
 */
public record BandVersionImpactResponse(int cohortSize, int changingStatus) {
}
