package com.flowforge.domain.workflow.portal.port.in;

import java.util.Map;

/** Soumission de la réponse du tiers : complète la tâche (US-1, D-PORTAL-FORM). */
public interface SubmitPortalTaskUseCase {

    void submit(String token, Map<String, Object> answers);
}
