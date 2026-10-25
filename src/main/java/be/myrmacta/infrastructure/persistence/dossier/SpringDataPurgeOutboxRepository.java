package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.model.PurgeOutboxStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPurgeOutboxRepository extends JpaRepository<PurgeOutboxJpaEntity, UUID> {
    boolean existsByDossierId(UUID dossierId);

    List<PurgeOutboxJpaEntity> findByStatusInOrderByCreatedAtAsc(
            Collection<PurgeOutboxStatus> statuses, Pageable pageable);

    List<PurgeOutboxJpaEntity> findByStatusOrderByCreatedAtAsc(PurgeOutboxStatus status, Pageable pageable);

    List<PurgeOutboxJpaEntity> findAllByOrderByCreatedAtAsc(Pageable pageable);

    Page<PurgeOutboxJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PurgeOutboxJpaEntity> findByStatusOrderByCreatedAtDesc(PurgeOutboxStatus status, Pageable pageable);

    long countByStatus(PurgeOutboxStatus status);

    @Query("select min(e.createdAt) from PurgeOutboxJpaEntity e where e.status in :statuses")
    Optional<Instant> findOldestCreatedAtByStatusIn(@Param("statuses") Collection<PurgeOutboxStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update PurgeOutboxJpaEntity e
               set e.status = :processing, e.updatedAt = :now
             where e.id = :id
               and e.status in :retryable
            """)
    int claim(
            @Param("id") UUID id,
            @Param("processing") PurgeOutboxStatus processing,
            @Param("retryable") Collection<PurgeOutboxStatus> retryable,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update PurgeOutboxJpaEntity e
               set e.status = :completed, e.lastError = null, e.updatedAt = :now
             where e.id = :id
            """)
    int markCompleted(
            @Param("id") UUID id, @Param("completed") PurgeOutboxStatus completed, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update PurgeOutboxJpaEntity e
               set e.status = :failed, e.lastError = :error, e.updatedAt = :now
             where e.id = :id
            """)
    int markFailed(
            @Param("id") UUID id,
            @Param("failed") PurgeOutboxStatus failed,
            @Param("error") String error,
            @Param("now") Instant now);

    /** Requeues rows stuck in {@code PROCESSING} since before {@code threshold} back to {@code FAILED}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update PurgeOutboxJpaEntity e
               set e.status = :failed, e.lastError = :error, e.updatedAt = :now
             where e.status = :processing
               and e.updatedAt < :threshold
            """)
    int requeueStaleProcessing(
            @Param("failed") PurgeOutboxStatus failed,
            @Param("processing") PurgeOutboxStatus processing,
            @Param("error") String error,
            @Param("now") Instant now,
            @Param("threshold") Instant threshold);
}
