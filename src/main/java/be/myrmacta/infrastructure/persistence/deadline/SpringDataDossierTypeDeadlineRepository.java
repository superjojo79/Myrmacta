package be.myrmacta.infrastructure.persistence.deadline;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataDossierTypeDeadlineRepository
        extends JpaRepository<DossierTypeDeadlineJpaEntity, DossierTypeDeadlineJpaEntity.Key> {

    List<DossierTypeDeadlineJpaEntity> findByTenantIdOrderByDossierTypeCodeAsc(String tenantId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DossierTypeDeadlineJpaEntity d where d.tenantId = :tenantId and d.dossierTypeCode = :code")
    int deleteOfType(@Param("tenantId") String tenantId, @Param("code") String dossierTypeCode);
}
