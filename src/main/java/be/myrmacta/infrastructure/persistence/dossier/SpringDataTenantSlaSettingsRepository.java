package be.myrmacta.infrastructure.persistence.dossier;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTenantSlaSettingsRepository extends JpaRepository<TenantSlaSettingsJpaEntity, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TenantSlaSettingsJpaEntity s
            set s.lastAlertScanAt = :scannedAt
            where s.tenantId = :tenantId
            """)
    int advanceLastAlertScanAt(@Param("tenantId") String tenantId, @Param("scannedAt") Instant scannedAt);

    /** D5 : avance {@code lastBiExportAt} seulement s'il vaut encore la valeur observée (compare-and-set). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TenantSlaSettingsJpaEntity s
            set s.lastBiExportAt = :next
            where s.tenantId = :tenantId and s.lastBiExportAt = :expected
            """)
    int advanceLastBiExportAt(
            @Param("tenantId") String tenantId, @Param("expected") Instant expected, @Param("next") Instant next);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TenantSlaSettingsJpaEntity s
            set s.lastBiExportAt = :next
            where s.tenantId = :tenantId and s.lastBiExportAt is null
            """)
    int advanceFirstBiExport(@Param("tenantId") String tenantId, @Param("next") Instant next);
}
