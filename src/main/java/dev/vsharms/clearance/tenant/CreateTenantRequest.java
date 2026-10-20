package dev.vsharms.clearance.tenant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(
        @NotBlank String name,
        @NotBlank String agency,
        @NotBlank String idpIssuer,
        @NotBlank String clientId,
        @NotBlank String clientSecret, // goes straight to the secret store, never the database
        boolean mfaEnforced,
        boolean encryptionAtRest,
        @Min(0) int auditRetentionDays) {

    // keep the secret out of logs if this record ever gets printed
    @Override
    public String toString() {
        return "CreateTenantRequest[name=%s, agency=%s, idpIssuer=%s, clientId=%s, clientSecret=***]"
                .formatted(name, agency, idpIssuer, clientId);
    }
}
