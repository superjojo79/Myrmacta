package be.myrmacta.infrastructure.persistence.deadline;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTenantCalendarSourceRepository extends JpaRepository<TenantCalendarSourceJpaEntity, UUID> {

    @Query("select s from TenantCalendarSourceJpaEntity s where s.tenantId = :tenantId order by lower(s.name)")
    List<TenantCalendarSourceJpaEntity> findAllOfTenant(@Param("tenantId") String tenantId);

    Optional<TenantCalendarSourceJpaEntity> findByIdAndTenantId(UUID id, String tenantId);

    /** Les périodes de la source suivent par la cascade de la clé étrangère. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TenantCalendarSourceJpaEntity s where s.tenantId = :tenantId and s.id = :id")
    int deleteOfTenant(@Param("tenantId") String tenantId, @Param("id") UUID id);
}
