package dev.vsharms.clearance.tenant;

import dev.vsharms.clearance.audit.AuditService;
import dev.vsharms.clearance.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
@Import(SecurityConfig.class)
class TenantControllerTest {

    private static final String BODY = """
            {"name":"Records Portal","agency":"Dept. of Example","idpIssuer":"https://idp.example.gov",
             "clientId":"portal","clientSecret":"s3cret","mfaEnforced":true,"encryptionAtRest":true,
             "auditRetentionDays":365}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean TenantService service;
    @MockitoBean AuditService audit;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void rejectsCallsWithoutAToken() throws Exception {
        mvc.perform(post("/api/tenants").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void auditorsCannotSubmitTenants() throws Exception {
        mvc.perform(post("/api/tenants").header("Idempotency-Key", "k1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminsCanSubmitAndRetriesReturn200() throws Exception {
        Tenant tenant = new Tenant("Records Portal", "Dept. of Example", "https://idp.example.gov",
                "portal", true, true, 365, "k1");
        when(service.submit(any(), eq("k1"), any()))
                .thenReturn(new TenantService.Submission(tenant, true))
                .thenReturn(new TenantService.Submission(tenant, false));

        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ONBOARDING_ADMIN"));
        mvc.perform(post("/api/tenants").header("Idempotency-Key", "k1").with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/tenants").header("Idempotency-Key", "k1").with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void missingIdempotencyKeyIsABadRequest() throws Exception {
        mvc.perform(post("/api/tenants")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ONBOARDING_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }
}
