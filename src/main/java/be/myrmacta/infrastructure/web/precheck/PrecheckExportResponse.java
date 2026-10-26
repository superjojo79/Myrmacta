package be.myrmacta.infrastructure.web.precheck;

import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import java.util.UUID;

/**
 * TODO §106 (lot B, HC10, US5) — journal d'une exécution pour un audit ou une AIPD : exécution, constats et décisions,
 * spécification appliquée, version du prompt et empreinte SHA-256 des entrées ; jamais les entrées en clair.
 */
record PrecheckExportResponse(UUID dossierId, UUID workflowLinkId, String inputSha256, PrecheckSpec spec,
                              PrecheckRunResponse run) {

    static PrecheckExportResponse from(PrecheckRun run) {
        return new PrecheckExportResponse(run.dossierId(), run.workflowLinkId(), run.inputSha256(), run.spec(),
                PrecheckRunResponse.from(run));
    }
}
