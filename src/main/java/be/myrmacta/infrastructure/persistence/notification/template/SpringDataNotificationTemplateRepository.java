package be.myrmacta.infrastructure.persistence.notification.template;

import be.myrmacta.domain.document.model.TemplateStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataNotificationTemplateRepository extends JpaRepository<NotificationTemplateJpaEntity, UUID> {

    Optional<NotificationTemplateJpaEntity> findByIdAndTenantId(UUID id, String tenantId);

    Page<NotificationTemplateJpaEntity> findByTenantIdOrderByUpdatedAtDesc(String tenantId, Pageable pageable);

    Page<NotificationTemplateJpaEntity> findByTenantIdAndTemplateKeyOrderByUpdatedAtDesc(
            String tenantId, String templateKey, Pageable pageable);

    @Query(
            """
            SELECT e FROM NotificationTemplateJpaEntity e
            WHERE e.tenantId = :tenantId
              AND e.templateKey = :templateKey
              AND e.status = :status
              AND ((:variantKey IS NULL AND e.variantKey IS NULL) OR e.variantKey = :variantKey)
              AND ((:locale IS NULL AND e.locale IS NULL) OR e.locale = :locale)
            """)
    Optional<NotificationTemplateJpaEntity> findActiveExact(
            @Param("tenantId") String tenantId,
            @Param("templateKey") String templateKey,
            @Param("variantKey") String variantKey,
            @Param("locale") String locale,
            @Param("status") TemplateStatus status);

    @Query(
            """
            SELECT COALESCE(MAX(e.version), 0) FROM NotificationTemplateJpaEntity e
            WHERE e.tenantId = :tenantId
              AND e.templateKey = :templateKey
              AND ((:variantKey IS NULL AND e.variantKey IS NULL) OR e.variantKey = :variantKey)
              AND ((:locale IS NULL AND e.locale IS NULL) OR e.locale = :locale)
            """)
    int maxVersion(
            @Param("tenantId") String tenantId,
            @Param("templateKey") String templateKey,
            @Param("variantKey") String variantKey,
            @Param("locale") String locale);

    /**
     * Cascade à quatre niveaux, comme les templates de document : (variante, langue), (variante, défaut), (défaut,
     * langue), (défaut, défaut). La première ligne du classement gagne.
     */
    @Query(
            value =
                    """
                    SELECT * FROM notification_template t
                    WHERE t.tenant_id = :tenantId
                      AND t.template_key = :templateKey
                      AND t.status = 'ACTIVE'
                      AND (
                        (((t.variant_key IS NULL AND CAST(:variantKey AS varchar) IS NULL)
                            OR t.variant_key = :variantKey)
                          AND ((t.locale IS NULL AND CAST(:locale AS varchar) IS NULL)
                            OR t.locale = :locale))
                        OR (((t.variant_key IS NULL AND CAST(:variantKey AS varchar) IS NULL)
                            OR t.variant_key = :variantKey)
                          AND t.locale IS NULL)
                        OR (t.variant_key IS NULL
                          AND ((t.locale IS NULL AND CAST(:locale AS varchar) IS NULL)
                            OR t.locale = :locale))
                        OR (t.variant_key IS NULL AND t.locale IS NULL)
                      )
                    ORDER BY CASE
                      WHEN (((t.variant_key IS NULL AND CAST(:variantKey AS varchar) IS NULL)
                            OR t.variant_key = :variantKey)
                          AND ((t.locale IS NULL AND CAST(:locale AS varchar) IS NULL)
                            OR t.locale = :locale)) THEN 1
                      WHEN (((t.variant_key IS NULL AND CAST(:variantKey AS varchar) IS NULL)
                            OR t.variant_key = :variantKey)
                          AND t.locale IS NULL) THEN 2
                      WHEN (t.variant_key IS NULL
                          AND ((t.locale IS NULL AND CAST(:locale AS varchar) IS NULL)
                            OR t.locale = :locale)) THEN 3
                      WHEN (t.variant_key IS NULL AND t.locale IS NULL) THEN 4
                      ELSE 99
                    END
                    FETCH FIRST 1 ROW ONLY
                    """,
            nativeQuery = true)
    Optional<NotificationTemplateJpaEntity> findBestActive(
            @Param("tenantId") String tenantId,
            @Param("templateKey") String templateKey,
            @Param("variantKey") String variantKey,
            @Param("locale") String locale);
}
