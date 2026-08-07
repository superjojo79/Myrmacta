package com.flowforge.infrastructure.persistence.notification;

import com.flowforge.domain.notification.model.NotificationOutboxStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
            order by e.nextAttemptAt asc
            """)
    List<NotificationOutboxJpaEntity> findEligiblePending(
            @Param("status") NotificationOutboxStatus status,
            @Param("now") Instant now,
            Pageable pageable);

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
}
