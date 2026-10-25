package be.myrmacta.infrastructure.persistence.reporting;

import be.myrmacta.domain.reporting.model.BiExportKind;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataBiExportFileRepository extends JpaRepository<BiExportFileJpaEntity, UUID> {

    @Query("""
            select f from BiExportFileJpaEntity f
            where f.tenantId = :tenantId
            order by f.createdAt desc
            """)
    Page<BiExportFileJpaEntity> pageAll(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("""
            select f from BiExportFileJpaEntity f
            where f.tenantId = :tenantId and f.kind = :sla
            order by f.createdAt desc
            """)
    Page<BiExportFileJpaEntity> pageSla(
            @Param("tenantId") String tenantId, @Param("sla") BiExportKind sla, Pageable pageable);

    @Query("""
            select f from BiExportFileJpaEntity f
            where f.tenantId = :tenantId
              and (f.kind = :sla or f.dossierTypeCode in :codes)
            order by f.createdAt desc
            """)
    Page<BiExportFileJpaEntity> pageVisible(
            @Param("tenantId") String tenantId,
            @Param("sla") BiExportKind sla,
            @Param("codes") Collection<String> codes,
            Pageable pageable);

    Optional<BiExportFileJpaEntity> findByTenantIdAndId(String tenantId, UUID id);

    List<BiExportFileJpaEntity> findByTenantIdAndCreatedAtBefore(String tenantId, Instant cutoff);
}
