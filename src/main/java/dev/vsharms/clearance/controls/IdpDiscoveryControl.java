package dev.vsharms.clearance.controls;

import dev.vsharms.clearance.config.ClearanceProperties;
import dev.vsharms.clearance.tenant.Tenant;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Confirms the tenant's identity provider is a real OIDC issuer we can trust:
 * reachable discovery document, matching issuer, a JWKS endpoint, and no unsigned ("none") tokens.
 */
@Component
@Order(4)
public class IdpDiscoveryControl implements Control {

    private final RestClient http;
    private final boolean allowInsecure;

    public IdpDiscoveryControl(RestClient.Builder builder, ClearanceProperties props) {
        this.http = builder.build();
        this.allowInsecure = props.allowInsecureIdp();
    }

    @Override public String id() { return "IA-8"; }
    @Override public String title() { return "Identification and authentication of external users (OIDC IdP)"; }

    @Override
    public ControlResult evaluate(Tenant tenant) {
        String issuer = stripTrailingSlash(tenant.getIdpIssuer());

        if (!allowInsecure && !issuer.startsWith("https://")) {
            return ControlResult.fail(id(), "IdP issuer must use https");
        }

        Map<String, Object> discovery;
        try {
            discovery = fetchDiscovery(issuer);
        } catch (RestClientException e) {
            // can't tell yet whether the tenant passes, so let the worker retry later
            throw new TransientCheckException("Could not reach IdP discovery for " + issuer, e);
        }
        if (discovery == null) {
            return ControlResult.fail(id(), "IdP returned an empty discovery document");
        }

        if (!issuer.equals(stripTrailingSlash(String.valueOf(discovery.get("issuer"))))) {
            return ControlResult.fail(id(), "Discovery issuer does not match the registered issuer");
        }
        if (discovery.get("jwks_uri") == null) {
            return ControlResult.fail(id(), "IdP does not publish a jwks_uri for token signature checks");
        }
        Object algs = discovery.get("id_token_signing_alg_values_supported");
        if (algs instanceof List<?> list && list.stream().anyMatch(a -> "none".equalsIgnoreCase(String.valueOf(a)))) {
            return ControlResult.fail(id(), "IdP allows unsigned tokens (alg=none)");
        }
        return ControlResult.pass(id(), "OIDC discovery verified for " + issuer);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> fetchDiscovery(String issuer) {
        return http.get()
                .uri(URI.create(issuer + "/.well-known/openid-configuration"))
                .retrieve()
                .body(Map.class);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
