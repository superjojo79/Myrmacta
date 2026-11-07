package be.myrmacta.infrastructure.persistence.deadline;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTenantCalendarRepository extends JpaRepository<TenantCalendarJpaEntity, String> {

    /** Crée la ligne avec les défauts si elle manque, sans erreur si une autre transaction la crée en même temps. */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into tenant_calendar (tenant_id, zone_id, weekend, updated_at)
            values (:tenantId, 'UTC', 'SATURDAY_SUNDAY', now())
            on conflict (tenant_id) do nothing
            """)
    int insertDefaultsIfAbsent(@Param("tenantId") String tenantId);

    /** Verrou de la ligne jusqu'à la fin de la transaction : les écritures du calendrier se suivent. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TenantCalendarJpaEntity c where c.tenantId = :tenantId")
    Optional<TenantCalendarJpaEntity> lockByTenantId(@Param("tenantId") String tenantId);
}
