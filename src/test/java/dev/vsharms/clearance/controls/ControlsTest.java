package dev.vsharms.clearance.controls;

import dev.vsharms.clearance.config.ClearanceProperties;
import dev.vsharms.clearance.tenant.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControlsTest {

    private final ClearanceProperties strict = new ClearanceProperties(90, false, 5, 5, "memory");

    private static Tenant tenant(String issuer, boolean mfa, boolean encrypted, int retentionDays) {
        return new Tenant("Records Portal", "Dept. of Example", issuer, "portal-client",
                mfa, encrypted, retentionDays, "key-" + System.nanoTime());
    }

    @Test
    void mfaControlFailsWhenNotEnforced() {
        assertThat(new MfaControl().evaluate(tenant("https://idp", false, true, 365)).passed()).isFalse();
        assertThat(new MfaControl().evaluate(tenant("https://idp", true, true, 365)).passed()).isTrue();
    }

    @Test
    void encryptionControlFailsWhenDataIsUnencrypted() {
        assertThat(new EncryptionAtRestControl().evaluate(tenant("https://idp", true, false, 365)).passed()).isFalse();
    }

    @Test
    void auditRetentionUsesConfiguredMinimum() {
        AuditRetentionControl control = new AuditRetentionControl(strict);
        assertThat(control.evaluate(tenant("https://idp", true, true, 89)).passed()).isFalse();
        assertThat(control.evaluate(tenant("https://idp", true, true, 90)).passed()).isTrue();
    }

    @Test
    void idpControlRejectsPlainHttpInStrictMode() {
        IdpDiscoveryControl control = idpReturning(Map.of());
        assertThat(control.evaluate(tenant("http://idp.example.gov", true, true, 365)).passed()).isFalse();
    }

    @Test
    void idpControlPassesForValidDiscoveryDocument() {
        IdpDiscoveryControl control = idpReturning(Map.of(
                "issuer", "https://idp.example.gov",
                "jwks_uri", "https://idp.example.gov/jwks",
                "id_token_signing_alg_values_supported", List.of("RS256")));
        assertThat(control.evaluate(tenant("https://idp.example.gov/", true, true, 365)).passed()).isTrue();
    }

    @Test
    void idpControlRejectsUnsignedTokens() {
        IdpDiscoveryControl control = idpReturning(Map.of(
                "issuer", "https://idp.example.gov",
                "jwks_uri", "https://idp.example.gov/jwks",
                "id_token_signing_alg_values_supported", List.of("RS256", "none")));
        ControlResult result = control.evaluate(tenant("https://idp.example.gov", true, true, 365));
        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("alg=none");
    }

    @Test
    void idpControlRejectsIssuerMismatch() {
        IdpDiscoveryControl control = idpReturning(Map.of(
                "issuer", "https://attacker.example.com",
                "jwks_uri", "https://attacker.example.com/jwks"));
        assertThat(control.evaluate(tenant("https://idp.example.gov", true, true, 365)).passed()).isFalse();
    }

    @Test
    void idpOutageIsTransientNotAFailure() {
        IdpDiscoveryControl control = new IdpDiscoveryControl(RestClient.builder(), strict) {
            @Override
            protected Map<String, Object> fetchDiscovery(String issuer) {
                throw new ResourceAccessException("connection refused");
            }
        };
        assertThatThrownBy(() -> control.evaluate(tenant("https://idp.example.gov", true, true, 365)))
                .isInstanceOf(TransientCheckException.class);
    }

    // swaps out the network call so these tests run offline
    private IdpDiscoveryControl idpReturning(Map<String, Object> discovery) {
        return new IdpDiscoveryControl(RestClient.builder(), strict) {
            @Override
            protected Map<String, Object> fetchDiscovery(String issuer) {
                return discovery;
            }
        };
    }
}
