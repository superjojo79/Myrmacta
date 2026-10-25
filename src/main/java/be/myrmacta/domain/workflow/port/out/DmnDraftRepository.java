package be.myrmacta.domain.workflow.port.out;

import be.myrmacta.domain.workflow.model.DmnDraft;
import java.util.Optional;

public interface DmnDraftRepository {

    Optional<DmnDraft> findByTenantIdAndDecisionKey(String tenantId, String decisionKey);

    /** Upsert: overwrites any existing draft for the same {@code (tenantId, decisionKey)}. */
    DmnDraft save(DmnDraft draft);

    /** No-op if no draft exists for this key. */
    void deleteByTenantIdAndDecisionKey(String tenantId, String decisionKey);

    /** TODO §82 (D6) : tous les brouillons du tenant. */
    java.util.List<DmnDraft> findAllByTenantId(String tenantId);

    /** TODO §82 (D6) : purge les brouillons d'origine automatisée non modifiés depuis {@code cutoff} (jamais ceux de l'éditeur). */
    int deleteAutomatedOlderThan(String tenantId, java.time.Instant cutoff);
}
