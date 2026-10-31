package be.myrmacta.domain.mailroom.port.in;

import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting;
import java.util.Optional;

/** TODO §113 (D-MAILROOM-REPLY-WORKFLOW) — administrer le workflow « réponse » d'un type de dossier. */
public interface ManageReplyWorkflowUseCase {

    Optional<ReplyWorkflowSetting> get(String dossierTypeCode);

    /** Vérifie le type, la configuration du workflow et, en mode enfant, le type enfant ; puis enregistre. */
    ReplyWorkflowSetting set(String dossierTypeCode, ReplyWorkflowSetting.Mode mode, String workflowKey, String childTypeCode);

    void clear(String dossierTypeCode);
}
