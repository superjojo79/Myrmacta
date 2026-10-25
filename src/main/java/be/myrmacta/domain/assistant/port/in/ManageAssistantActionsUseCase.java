package be.myrmacta.domain.assistant.port.in;

import be.myrmacta.domain.assistant.model.AssistantAction;
import be.myrmacta.domain.dossier.model.AccessContext;
import java.util.List;
import java.util.UUID;

/** TODO §86 (LC3, revue M2) — l'humain relit puis applique (ou écarte) ce que l'assistant propose d'écrire. */
public interface ManageAssistantActionsUseCase {

    List<AssistantAction> list(AccessContext.User actor, UUID conversationId);

    /** Exécute l'écriture avec l'identité de l'utilisateur ; une action ne s'applique qu'une fois. */
    AssistantAction apply(AccessContext.User actor, UUID conversationId, UUID actionId);

    AssistantAction dismiss(AccessContext.User actor, UUID conversationId, UUID actionId);
}
