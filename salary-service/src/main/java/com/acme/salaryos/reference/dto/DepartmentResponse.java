package com.acme.salaryos.reference.dto;

import java.util.UUID;

public record DepartmentResponse(UUID id, String name, String code, UUID parentId) {
}
