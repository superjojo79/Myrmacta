package be.myrmacta.infrastructure.persistence.mailroom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataDossierTypeReplyWorkflowRepository
        extends JpaRepository<DossierTypeReplyWorkflowJpaEntity, DossierTypeReplyWorkflowJpaEntity.Key> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DossierTypeReplyWorkflowJpaEntity w where w.tenantId = :tenantId and w.dossierTypeCode = :code")
    int deleteOfType(@Param("tenantId") String tenantId, @Param("code") String dossierTypeCode);
}
