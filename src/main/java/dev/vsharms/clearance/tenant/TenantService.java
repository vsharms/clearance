package dev.vsharms.clearance.tenant;

import dev.vsharms.clearance.audit.AuditService;
import dev.vsharms.clearance.queue.OnboardingJob;
import dev.vsharms.clearance.queue.OnboardingJobRepository;
import dev.vsharms.clearance.secrets.SecretStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenants;
    private final OnboardingJobRepository jobs;
    private final SecretStore secrets;
    private final AuditService audit;

    public TenantService(TenantRepository tenants, OnboardingJobRepository jobs,
                         SecretStore secrets, AuditService audit) {
        this.tenants = tenants;
        this.jobs = jobs;
        this.secrets = secrets;
        this.audit = audit;
    }

    /** Result of a submit call; created=false means the idempotency key was already used. */
    public record Submission(Tenant tenant, boolean created) {}

    @Transactional
    public Submission submit(CreateTenantRequest req, String idempotencyKey, String actor) {
        // a retried request with the same key gets the original tenant back instead of a duplicate
        Optional<Tenant> existing = tenants.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new Submission(existing.get(), false);
        }

        Tenant tenant = new Tenant(req.name(), req.agency(), req.idpIssuer(), req.clientId(),
                req.mfaEnforced(), req.encryptionAtRest(), req.auditRetentionDays(), idempotencyKey);
        try {
            tenants.saveAndFlush(tenant);
        } catch (DataIntegrityViolationException race) {
            // two replicas raced on the same key; the unique constraint picked a winner
            return new Submission(tenants.findByIdempotencyKey(idempotencyKey).orElseThrow(), false);
        }

        secrets.put("tenants/" + tenant.getId(), "client_secret", req.clientSecret());
        jobs.save(new OnboardingJob(tenant.getId()));
        audit.record(tenant.getId(), actor, "TENANT_SUBMITTED", "Onboarding requested for " + tenant.getAgency());
        return new Submission(tenant, true);
    }

    @Transactional(readOnly = true)
    public Optional<Tenant> find(UUID id) {
        return tenants.findById(id);
    }
}
