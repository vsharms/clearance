package dev.vsharms.clearance.audit;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    public void record(UUID tenantId, String actor, String action, String detail) {
        events.save(new AuditEvent(tenantId, actor, action, detail));
    }

    public List<AuditEvent> trailFor(UUID tenantId) {
        return events.findByTenantIdOrderByIdAsc(tenantId);
    }
}
