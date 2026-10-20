package dev.vsharms.clearance.audit;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Append-only record of who did what to a tenant, and why. Never updated or deleted. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false, length = 2000)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    public AuditEvent(UUID tenantId, String actor, String action, String detail) {
        this.tenantId = tenantId;
        this.actor = actor;
        this.action = action;
        this.detail = detail;
        this.occurredAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public Instant getOccurredAt() { return occurredAt; }
}
