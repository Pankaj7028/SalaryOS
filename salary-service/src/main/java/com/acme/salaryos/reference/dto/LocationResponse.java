package com.acme.salaryos.reference.dto;

import java.util.UUID;

public record LocationResponse(UUID id, String countryCode, String city, String name, boolean isActive) {
}
