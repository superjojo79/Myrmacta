package com.flowforge.infrastructure.persistence.notification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataUserNotificationRepository extends JpaRepository<UserNotificationJpaEntity, UUID> {

    Page<UserNotificationJpaEntity> findByTenantIdAndRecipientOrderByCreatedAtDesc(
            String tenantId, String recipient, Pageable pageable);

    long countByTenantIdAndRecipientAndReadAtIsNull(String tenantId, String recipient);

    Optional<UserNotificationJpaEntity> findByIdAndTenantIdAndRecipient(UUID id, String tenantId, String recipient);

    @Modifying
    @Query(
            """
            update UserNotificationJpaEntity e set e.readAt = :now
            where e.tenantId = :tenantId and e.recipient = :recipient and e.readAt is null
            """)
    int markAllRead(@Param("tenantId") String tenantId, @Param("recipient") String recipient, @Param("now") Instant now);

    // TODO §66 F5 (Phase 10 adversarial review): a derived deleteBy... here loads every matching
    // row into memory then issues one DELETE per id (proven via captured SQL) — with 30-day
    // retention and volumes up to ~4x the outbox's, that's N round trips in one transaction. Bulk
    // delete instead, mirroring SpringDataNotificationOutboxRepository#deleteTerminalOlderThan.
    @Modifying(clearAutomatically = true)
    @Query("delete from UserNotificationJpaEntity e where e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
