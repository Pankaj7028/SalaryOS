package com.acme.salaryos.reference.dto;

import java.util.UUID;

public record JobLevelResponse(UUID id, UUID jobFamilyId, String levelCode, String title, int sortOrder) {
}
