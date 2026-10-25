package be.myrmacta.domain.workflow.portal.port.in;

import be.myrmacta.domain.workflow.portal.model.PortalCredential;
import java.util.Map;

/** Soumission de la réponse du tiers : complète la tâche (US-1, D-PORTAL-FORM). */
public interface SubmitPortalTaskUseCase {

    void submit(PortalCredential credential, Map<String, Object> answers);

    default void submit(String token, Map<String, Object> answers) {
        submit(PortalCredential.of(token, null), answers);
    }
}
