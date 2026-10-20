package dev.vsharms.clearance.queue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingJobRepository extends JpaRepository<OnboardingJob, Long> {

    /**
     * The heart of the distributed queue. Each replica locks the rows it grabs; SKIP LOCKED makes
     * other replicas pass over those rows instead of waiting, so no job is ever worked twice at once.
     */
    @Query(value = """
            SELECT * FROM onboarding_jobs
            WHERE status = 'QUEUED' AND run_after <= now()
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OnboardingJob> claimBatch(@Param("limit") int limit);

    Optional<OnboardingJob> findByTenantId(UUID tenantId);
}
