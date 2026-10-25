package be.myrmacta.infrastructure.persistence.reporting;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSlaAlertStateRepository
        extends JpaRepository<SlaAlertStateJpaEntity, SlaAlertStateJpaEntity.Key> {

    Optional<SlaAlertStateJpaEntity> findByTenantIdAndDossierId(String tenantId, UUID dossierId);

    void deleteByTenantId(String tenantId);

    /** Insertion stricte : une clé déjà prise (autre exécutant) lève une violation d'unicité, jamais une fusion silencieuse.
     * Transaction PROPRE (revue D5 passe 2) : sous la transaction de l'appelant (UnitOfWork du job), la violation la marquerait
     * rollback-only et le perdant échouerait au commit au lieu de rendre la main sans notifier. */
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
            insert into SlaAlertStateJpaEntity (tenantId, dossierId, lastStatus, updatedAt)
            values (:tenantId, :dossierId, :status, :now)
            """)
    int insertStrict(
            @org.springframework.data.repository.query.Param("tenantId") String tenantId,
            @org.springframework.data.repository.query.Param("dossierId") UUID dossierId,
            @org.springframework.data.repository.query.Param("status") be.myrmacta.domain.reporting.model.SlaStatus status,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
            update SlaAlertStateJpaEntity e set e.lastStatus = :next, e.updatedAt = :now
            where e.tenantId = :tenantId and e.dossierId = :dossierId and e.lastStatus = :expected
            """)
    int transition(
            @org.springframework.data.repository.query.Param("tenantId") String tenantId,
            @org.springframework.data.repository.query.Param("dossierId") UUID dossierId,
            @org.springframework.data.repository.query.Param("expected") be.myrmacta.domain.reporting.model.SlaStatus expected,
            @org.springframework.data.repository.query.Param("next") be.myrmacta.domain.reporting.model.SlaStatus next,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);
}
