package com.flowforge.infrastructure.persistence.notification;

import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationOutboxStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataNotificationOutboxRepository extends JpaRepository<NotificationOutboxJpaEntity, UUID> {

    @Query(
            """
            select e from NotificationOutboxJpaEntity e
            where e.status = :status
              and e.nextAttemptAt <= :now
            order by e.nextAttemptAt asc, e.id asc
            """)
    List<NotificationOutboxJpaEntity> findEligiblePending(
            @Param("status") NotificationOutboxStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    /**
     * D5 (TODO §79) : prise d'une ligne par compare-and-set sur {@code next_attempt_at} — la ligne observée est « louée »
     * jusqu'à {@code leaseUntil} ; un second exécutant qui l'avait lue obtient 0 ligne modifiée et la saute. Sans nouveau
     * statut ni migration : une ligne abandonnée par un exécutant mort redevient éligible à l'échéance du bail.
     */
    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true)
    @Query(
            """
            update NotificationOutboxJpaEntity e
               set e.nextAttemptAt = :leaseUntil, e.updatedAt = :now
             where e.id = :id
               and e.status = :status
               and e.nextAttemptAt = :observedNextAttemptAt
            """)
    int claim(
            @Param("id") UUID id,
            @Param("status") NotificationOutboxStatus status,
            @Param("observedNextAttemptAt") Instant observedNextAttemptAt,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query(
            """
            delete from NotificationOutboxJpaEntity e
            where e.status in :statuses
              and coalesce(e.processedAt, e.updatedAt) < :cutoff
            """)
    int deleteTerminalOlderThan(
            @Param("statuses") Collection<NotificationOutboxStatus> statuses,
            @Param("cutoff") Instant cutoff);

    @Query("select min(e.createdAt) from NotificationOutboxJpaEntity e where e.status = :status")
    Optional<Instant> findOldestCreatedAtByStatus(@Param("status") NotificationOutboxStatus status);

    long countByChannelAndStatus(NotificationChannel channel, NotificationOutboxStatus status);
}
