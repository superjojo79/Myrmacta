package com.flowforge.infrastructure.persistence.dossier;

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
}
