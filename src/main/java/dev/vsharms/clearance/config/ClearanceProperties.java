package dev.vsharms.clearance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for onboarding. Defaults lean strict; the dev profile loosens IdP https checks for local Keycloak.
 */
@ConfigurationProperties(prefix = "clearance")
public record ClearanceProperties(
        int minAuditRetentionDays,
        boolean allowInsecureIdp,
        int workerBatchSize,
        int maxAttempts,
        String secretStore) {

    public ClearanceProperties {
        if (minAuditRetentionDays <= 0) minAuditRetentionDays = 90;
        if (workerBatchSize <= 0) workerBatchSize = 5;
        if (maxAttempts <= 0) maxAttempts = 5;
        if (secretStore == null || secretStore.isBlank()) secretStore = "memory";
    }
}
