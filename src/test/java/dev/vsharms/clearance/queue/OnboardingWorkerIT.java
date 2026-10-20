package dev.vsharms.clearance.queue;

import dev.vsharms.clearance.audit.AuditEvent;
import dev.vsharms.clearance.audit.AuditService;
import dev.vsharms.clearance.controls.ControlResult;
import dev.vsharms.clearance.controls.IdpDiscoveryControl;
import dev.vsharms.clearance.tenant.CreateTenantRequest;
import dev.vsharms.clearance.tenant.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Simulates several replicas draining the queue at once and proves each tenant is decided exactly once.
 * Needs Docker; skipped automatically when it isn't available.
 */
@SpringBootTest(properties = "clearance.worker-poll-ms=3600000")
@Testcontainers(disabledWithoutDocker = true)
class OnboardingWorkerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TenantService tenants;
    @Autowired OnboardingWorker worker;
    @Autowired AuditService audit;
    @MockitoBean IdpDiscoveryControl idp;   // no real IdP in tests
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void concurrentWorkersDecideEachTenantExactlyOnce() throws Exception {
        when(idp.id()).thenReturn("IA-8");
        when(idp.evaluate(any())).thenReturn(ControlResult.pass("IA-8", "stubbed"));

        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            var req = new CreateTenantRequest("Tenant " + i, "Agency", "https://idp.example.gov", "c", "s",
                    true, true, 365);
            ids.add(tenants.submit(req, "key-" + i, "test").tenant().getId());
        }

        // four "replicas" hammering the queue at the same time
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Callable<Integer>> drains = new ArrayList<>();
        for (int w = 0; w < 4; w++) {
            drains.add(() -> {
                int total = 0, got;
                while ((got = worker.processBatch()) > 0) total += got;
                return total;
            });
        }
        int processed = 0;
        for (Future<Integer> f : pool.invokeAll(drains)) processed += f.get();
        pool.shutdown();

        assertThat(processed).isEqualTo(40);
        for (UUID id : ids) {
            long decisions = audit.trailFor(id).stream().map(AuditEvent::getAction)
                    .filter(a -> a.equals("TENANT_ACTIVE")).count();
            assertThat(decisions).as("decisions for %s", id).isEqualTo(1);
        }
    }

    @Test
    void sameIdempotencyKeyReturnsTheOriginalTenant() {
        var req = new CreateTenantRequest("Dup", "Agency", "https://idp.example.gov", "c", "s", true, true, 365);
        var first = tenants.submit(req, "dup-key", "test");
        var second = tenants.submit(req, "dup-key", "test");
        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.tenant().getId()).isEqualTo(first.tenant().getId());
    }
}
