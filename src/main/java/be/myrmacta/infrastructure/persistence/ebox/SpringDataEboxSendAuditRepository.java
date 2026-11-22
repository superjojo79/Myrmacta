package be.myrmacta.infrastructure.persistence.ebox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.Repository;

/**
 * TODO §155 (D-EBOX-SEND-AUDIT) — dépôt du journal d'audit des envois : ajout et lecture seulement, aucune méthode de
 * modification ni de suppression n'est exposée.
 */
interface SpringDataEboxSendAuditRepository extends Repository<EboxSendAuditJpaEntity, UUID> {

    EboxSendAuditJpaEntity save(EboxSendAuditJpaEntity entity);

    List<EboxSendAuditJpaEntity> findByTenantIdAndIdempotencyKeyOrderByRecordedAtDesc(
            String tenantId, String idempotencyKey, Limit limit);
}
