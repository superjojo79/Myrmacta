package be.myrmacta.domain.precheck.port.in;

import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import java.util.Map;
import java.util.UUID;

/**
 * TODO §106 (issue #211, lot B) — exécution d'une pré-vérification : depuis la brique du workflow (job asynchrone) ou
 * depuis la file des relances (bouton, MCP). Le résultat est toujours rangé, même en échec (HC5).
 */
public interface RunDossierPrecheckUseCase {

    /** @param variables variables du process, pour les règles ({@code precheckRules}) */
    WorkflowResult runInWorkflow(UUID dossierId, UUID workflowLinkId, PrecheckSpec spec, Map<String, Object> variables);

    /** Exécution de la file, déjà prise avec un bail ; abandonnée après {@value #MAX_QUEUE_ATTEMPTS} tentatives. */
    PrecheckRun runQueued(PrecheckRun run);

    int MAX_QUEUE_ATTEMPTS = 2;

    /**
     * @param resultVariable HC6 — comptes <b>déterministes</b> seulement ({@code status}, {@code missing},
     *     {@code inconsistent}, {@code runId}) : un routage ne dépend jamais du modèle (C5)
     */
    record WorkflowResult(PrecheckRun run, Map<String, Object> resultVariable) {}
}
