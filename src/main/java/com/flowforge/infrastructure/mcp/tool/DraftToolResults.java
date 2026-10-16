package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.workflow.model.ConfigPublication;
import com.flowforge.domain.workflow.palette.InactiveBlockReference;
import com.flowforge.domain.workflow.port.in.ProposeDraftUseCase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TODO §82 (D6) — forme commune des réponses des outils de configuration (brouillon en attente, ou publié). */
final class DraftToolResults {

    static final String DRAFT_PENDING = "DRAFT_PENDING_PUBLICATION";
    static final String PUBLISHED = "PUBLISHED";
    static final String VALID = "VALID";

    private DraftToolResults() {}

    static Map<String, Object> of(
            String keyName,
            String key,
            boolean dryRun,
            String status,
            ProposeDraftUseCase.Proposal proposal,
            ConfigPublication publication) {
        return of(keyName, key, dryRun, status, proposal, publication,
                proposal == null ? List.of() : proposal.inactiveReferences());
    }

    /**
     * @param inactiveReferences TODO §93 D-DRAFT-REF-STATUS — ressources référencées qui existent mais ne sont pas actives :
     *     signalées à l'agent, car la publication les refusera tant qu'un humain ne les a pas activées
     */
    static Map<String, Object> of(
            String keyName,
            String key,
            boolean dryRun,
            String status,
            ProposeDraftUseCase.Proposal proposal,
            ConfigPublication publication,
            List<InactiveBlockReference> inactiveReferences) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(keyName, key);
        map.put("dryRun", dryRun);
        map.put("status", status);
        if (proposal != null) {
            map.put("draft", proposal);
        }
        if (publication != null) {
            map.put("publication", publication);
        }
        if (!inactiveReferences.isEmpty()) {
            map.put("inactiveReferences", inactiveReferences.stream()
                    .map(r -> Map.of("activityId", r.activityId(), "parameter", r.parameter(),
                            "source", r.source(), "value", r.value()))
                    .toList());
            map.put("inactiveReferencesMessage", "These referenced resources exist but are not active: publishing the "
                    + "workflow will be refused until a human activates them (e.g. activate the document template).");
        }
        if (DRAFT_PENDING.equals(status)) {
            map.put("message", "Saved as a draft: a human must publish it from the 'Brouillons à publier' screen. "
                    + "Nothing is deployed until then.");
        }
        return map;
    }
}
