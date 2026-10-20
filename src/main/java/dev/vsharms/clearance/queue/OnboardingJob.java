package dev.vsharms.clearance.queue;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** A unit of work in the Postgres-backed queue. One job per tenant onboarding request. */
@Entity
@Table(name = "onboarding_jobs")
public class OnboardingJob {

    public enum Status { QUEUED, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "run_after", nullable = false)
    private Instant runAfter;

    @Column(name = "processed_by")
    private String processedBy;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OnboardingJob() {
    }

    public OnboardingJob(UUID tenantId) {
        this.tenantId = tenantId;
        this.status = Status.QUEUED;
        this.attempts = 0;
        this.createdAt = Instant.now();
        this.runAfter = this.createdAt;
    }

    public void complete(String workerId) {
        this.status = Status.DONE;
        this.processedBy = workerId;
        this.attempts++;
    }

    /** Exponential backoff: 2s, 4s, 8s... capped at 5 minutes. */
    public void retryLater(String workerId, String error) {
        this.attempts++;
        this.processedBy = workerId;
        this.lastError = error;
        long seconds = Math.min(300, (long) Math.pow(2, attempts));
        this.runAfter = Instant.now().plus(Duration.ofSeconds(seconds));
    }

    public void giveUp(String workerId, String error) {
        this.status = Status.FAILED;
        this.processedBy = workerId;
        this.lastError = error;
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getRunAfter() { return runAfter; }
    public String getProcessedBy() { return processedBy; }
    public String getLastError() { return lastError; }
}
