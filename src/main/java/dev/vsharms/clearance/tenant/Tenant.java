package dev.vsharms.clearance.tenant;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String agency;

    @Column(name = "idp_issuer", nullable = false)
    private String idpIssuer;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "mfa_enforced", nullable = false)
    private boolean mfaEnforced;

    @Column(name = "encryption_at_rest", nullable = false)
    private boolean encryptionAtRest;

    @Column(name = "audit_retention_days", nullable = false)
    private int auditRetentionDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected Tenant() {
        // for JPA
    }

    public Tenant(String name, String agency, String idpIssuer, String clientId, boolean mfaEnforced,
                  boolean encryptionAtRest, int auditRetentionDays, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.agency = agency;
        this.idpIssuer = idpIssuer;
        this.clientId = clientId;
        this.mfaEnforced = mfaEnforced;
        this.encryptionAtRest = encryptionAtRest;
        this.auditRetentionDays = auditRetentionDays;
        this.idempotencyKey = idempotencyKey;
        this.status = TenantStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void decide(TenantStatus outcome) {
        if (status != TenantStatus.PENDING) {
            throw new IllegalStateException("Tenant " + id + " was already decided as " + status);
        }
        this.status = outcome;
        this.decidedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getAgency() { return agency; }
    public String getIdpIssuer() { return idpIssuer; }
    public String getClientId() { return clientId; }
    public boolean isMfaEnforced() { return mfaEnforced; }
    public boolean isEncryptionAtRest() { return encryptionAtRest; }
    public int getAuditRetentionDays() { return auditRetentionDays; }
    public TenantStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
