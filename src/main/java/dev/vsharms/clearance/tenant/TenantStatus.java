package dev.vsharms.clearance.tenant;

public enum TenantStatus {
    PENDING,   // submitted, waiting for a worker to pick it up
    ACTIVE,    // every control passed
    REJECTED,  // at least one control failed
    FAILED     // couldn't finish checks after max retries (e.g. IdP unreachable)
}
