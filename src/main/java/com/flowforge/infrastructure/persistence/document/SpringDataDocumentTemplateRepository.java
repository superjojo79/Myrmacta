package com.flowforge.infrastructure.persistence.document;

import com.flowforge.domain.document.model.TemplateStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataDocumentTemplateRepository extends JpaRepository<DocumentTemplateJpaEntity, UUID> {

    Optional<DocumentTemplateJpaEntity> findByIdAndTenantId(UUID id, String tenantId);

    List<DocumentTemplateJpaEntity> findByTenantIdOrderByTemplateKeyAscVersionDesc(String tenantId);

    List<DocumentTemplateJpaEntity> findByTenantIdAndTemplateKeyOrderByVersionDesc(
            String tenantId, String templateKey);

    Optional<DocumentTemplateJpaEntity> findByTenantIdAndTemplateKeyAndVariantKeyAndLocaleAndStatus(
            String tenantId,
            String templateKey,
            String variantKey,
            String locale,
            TemplateStatus status);

    @Query(
            """
            SELECT COALESCE(MAX(e.version), 0) FROM DocumentTemplateJpaEntity e
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

    @Query(
            value =
                    """
                    SELECT * FROM document_template t
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
    Optional<DocumentTemplateJpaEntity> findBestActive(
            @Param("tenantId") String tenantId,
            @Param("templateKey") String templateKey,
            @Param("variantKey") String variantKey,
            @Param("locale") String locale);
}
