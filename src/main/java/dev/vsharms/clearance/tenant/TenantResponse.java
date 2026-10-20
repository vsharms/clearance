package dev.vsharms.clearance.tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(UUID id, String name, String agency, TenantStatus status,
                             Instant createdAt, Instant decidedAt) {

    static TenantResponse from(Tenant t) {
        return new TenantResponse(t.getId(), t.getName(), t.getAgency(), t.getStatus(),
                t.getCreatedAt(), t.getDecidedAt());
    }
}
