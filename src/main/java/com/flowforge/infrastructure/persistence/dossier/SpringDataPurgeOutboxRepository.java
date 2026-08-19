package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.PurgeOutboxStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
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

    long countByStatus(PurgeOutboxStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update PurgeOutboxJpaEntity e
               set e.status = :processing
             where e.id = :id
               and e.status in :retryable
            """)
    int claim(
            @Param("id") UUID id,
            @Param("processing") PurgeOutboxStatus processing,
            @Param("retryable") Collection<PurgeOutboxStatus> retryable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update PurgeOutboxJpaEntity e
               set e.status = :completed, e.lastError = null
             where e.id = :id
            """)
    int markCompleted(@Param("id") UUID id, @Param("completed") PurgeOutboxStatus completed);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update PurgeOutboxJpaEntity e
               set e.status = :failed, e.lastError = :error
             where e.id = :id
            """)
    int markFailed(
            @Param("id") UUID id, @Param("failed") PurgeOutboxStatus failed, @Param("error") String error);
}
