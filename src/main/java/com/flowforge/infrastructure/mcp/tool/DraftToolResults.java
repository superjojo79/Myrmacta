package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.workflow.model.ConfigPublication;
import com.flowforge.domain.workflow.port.in.ProposeDraftUseCase;
import java.util.LinkedHashMap;
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
        if (DRAFT_PENDING.equals(status)) {
            map.put("message", "Saved as a draft: a human must publish it from the 'Brouillons à publier' screen. "
                    + "Nothing is deployed until then.");
        }
        return map;
    }
}
