package be.myrmacta.infrastructure.persistence.dossier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataDossierRelationRepository extends JpaRepository<DossierRelationJpaEntity, UUID> {

    @Query(
            """
            select r from DossierRelationJpaEntity r
            where r.tenantId = :tenantId
              and (r.sourceDossierId = :dossierId or r.targetDossierId = :dossierId)
            """)
    List<DossierRelationJpaEntity> findByDossier(
            @Param("tenantId") String tenantId, @Param("dossierId") UUID dossierId);

    Optional<DossierRelationJpaEntity> findByTenantIdAndSourceDossierIdAndIdempotencyKey(
            String tenantId, UUID sourceDossierId, String idempotencyKey);

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from DossierRelationJpaEntity r where r.sourceDossierId = :id or r.targetDossierId = :id")
    void deleteTouching(@Param("id") UUID id);
}
