package com.flowforge.infrastructure.persistence.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataNotificationWebhookRepository
        extends JpaRepository<NotificationWebhookJpaEntity, UUID> {

    Optional<NotificationWebhookJpaEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, String tenantId);

    Optional<NotificationWebhookJpaEntity> findByIdAndTenantId(UUID id, String tenantId);

    List<NotificationWebhookJpaEntity> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(String tenantId);

    @Query(
            """
            select case when count(e) > 0 then true else false end
            from NotificationWebhookJpaEntity e
            where e.tenantId = :tenantId
              and e.name = :name
              and e.deletedAt is null
              and (:excludeId is null or e.id <> :excludeId)
            """)
    boolean existsActiveByName(
            @Param("tenantId") String tenantId,
            @Param("name") String name,
            @Param("excludeId") UUID excludeId);
}
