package be.myrmacta.infrastructure.persistence.deadline;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTenantCalendarEntryRepository extends JpaRepository<TenantCalendarEntryJpaEntity, UUID> {

    List<TenantCalendarEntryJpaEntity> findByTenantId(String tenantId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TenantCalendarEntryJpaEntity e where e.tenantId = :tenantId and e.sourceId is null")
    int deleteManualOfTenant(@Param("tenantId") String tenantId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TenantCalendarEntryJpaEntity e where e.tenantId = :tenantId and e.sourceId = :sourceId")
    int deleteOfSource(@Param("tenantId") String tenantId, @Param("sourceId") UUID sourceId);
}
