package be.myrmacta.domain.workflow.port.out;

import be.myrmacta.domain.workflow.model.BpmnDraft;
import java.util.Optional;

public interface BpmnDraftRepository {

    Optional<BpmnDraft> findByTenantIdAndProcessDefinitionKey(String tenantId, String processDefinitionKey);

    /** Upsert: overwrites any existing draft for the same {@code (tenantId, processDefinitionKey)}. */
    BpmnDraft save(BpmnDraft draft);

    /** No-op if no draft exists for this key. */
    void deleteByTenantIdAndProcessDefinitionKey(String tenantId, String processDefinitionKey);

    /** TODO §82 (D6) : tous les brouillons du tenant (écran « Brouillons à publier »). */
    java.util.List<BpmnDraft> findAllByTenantId(String tenantId);

    /**
     * TODO §82 (D6) : purge les brouillons d'origine automatisée (provenance différente de {@code UI}) non modifiés depuis
     * {@code cutoff}. Idempotent, sans effet sur les brouillons de l'éditeur.
     *
     * @return nombre de brouillons supprimés
     */
    int deleteAutomatedOlderThan(String tenantId, java.time.Instant cutoff);
}
