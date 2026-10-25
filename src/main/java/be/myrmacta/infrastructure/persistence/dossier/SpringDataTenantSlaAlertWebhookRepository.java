package be.myrmacta.infrastructure.persistence.dossier;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTenantSlaAlertWebhookRepository
        extends JpaRepository<TenantSlaAlertWebhookJpaEntity, TenantSlaAlertWebhookJpaEntity.Key> {

    List<TenantSlaAlertWebhookJpaEntity> findByTenantId(String tenantId);

    void deleteByTenantId(String tenantId);
}
