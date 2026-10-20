package dev.vsharms.clearance.queue;

import dev.vsharms.clearance.audit.AuditService;
import dev.vsharms.clearance.config.ClearanceProperties;
import dev.vsharms.clearance.controls.ControlEvaluator;
import dev.vsharms.clearance.controls.ControlResult;
import dev.vsharms.clearance.controls.TransientCheckException;
import dev.vsharms.clearance.tenant.Tenant;
import dev.vsharms.clearance.tenant.TenantRepository;
import dev.vsharms.clearance.tenant.TenantStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Runs on every replica. Postgres row locks keep the replicas from stepping on each other. */
@Component
public class OnboardingWorker {

    private static final Logger log = LoggerFactory.getLogger(OnboardingWorker.class);

    private final OnboardingJobRepository jobs;
    private final TenantRepository tenants;
    private final ControlEvaluator evaluator;
    private final AuditService audit;
    private final MeterRegistry metrics;
    private final ClearanceProperties props;
    private final String workerId;

    public OnboardingWorker(OnboardingJobRepository jobs, TenantRepository tenants, ControlEvaluator evaluator,
                            AuditService audit, MeterRegistry metrics, ClearanceProperties props,
                            @Value("${HOSTNAME:local}") String workerId) {
        this.jobs = jobs;
        this.tenants = tenants;
        this.evaluator = evaluator;
        this.audit = audit;
        this.metrics = metrics;
        this.props = props;
        this.workerId = workerId; // container hostname, so the audit trail shows which replica decided
    }

    // @Transactional here too: poll() calls processBatch() on 'this', which skips Spring's proxy
    @Scheduled(fixedDelayString = "${clearance.worker-poll-ms:1000}")
    @Transactional
    public void poll() {
        processBatch();
    }

    /** Claims, processes, and commits a batch in one transaction so locks release only once results are saved. */
    @Transactional
    public int processBatch() {
        List<OnboardingJob> batch = jobs.claimBatch(props.workerBatchSize());
        for (OnboardingJob job : batch) {
            process(job);
        }
        return batch.size();
    }

    private void process(OnboardingJob job) {
        Tenant tenant = tenants.findById(job.getTenantId()).orElseThrow();
        try {
            List<ControlResult> results = evaluator.evaluate(tenant);
            results.forEach(r -> audit.record(tenant.getId(), workerId,
                    r.passed() ? "CONTROL_PASSED" : "CONTROL_FAILED", r.controlId() + ": " + r.reason()));

            boolean allPassed = results.stream().allMatch(ControlResult::passed);
            TenantStatus outcome = allPassed ? TenantStatus.ACTIVE : TenantStatus.REJECTED;
            tenant.decide(outcome);
            job.complete(workerId);
            audit.record(tenant.getId(), workerId, "TENANT_" + outcome, "Decision made after "
                    + results.size() + " controls");
            metrics.counter("clearance.onboarding.decisions", "outcome", outcome.name()).increment();
            log.info("tenant={} outcome={} worker={}", tenant.getId(), outcome, workerId);

        } catch (TransientCheckException e) {
            if (job.getAttempts() + 1 >= props.maxAttempts()) {
                job.giveUp(workerId, e.getMessage());
                tenant.decide(TenantStatus.FAILED);
                audit.record(tenant.getId(), workerId, "TENANT_FAILED", "Gave up: " + e.getMessage());
                metrics.counter("clearance.onboarding.decisions", "outcome", "FAILED").increment();
            } else {
                job.retryLater(workerId, e.getMessage());
                audit.record(tenant.getId(), workerId, "CHECK_RETRY_SCHEDULED", e.getMessage());
                metrics.counter("clearance.onboarding.retries").increment();
            }
        }
    }
}
