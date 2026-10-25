package be.myrmacta.domain.assistant.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import java.util.List;
import java.util.UUID;

public interface ChatWithAssistantUseCase {

    /** @param conversationId {@code null} ouvre une nouvelle conversation */
    ChatResult chat(AccessContext.User actor, UUID conversationId, String message);

    /** {@code producedDrafts} : au moins un outil a déposé un brouillon à relire (écran Admin → Brouillons). */
    record ChatResult(
            UUID conversationId,
            String reply,
            List<ToolActivity> activity,
            boolean producedDrafts,
            List<be.myrmacta.domain.assistant.model.AssistantAction> proposals) {}

    record ToolActivity(String tool, boolean ok, String arguments, String summary) {}
}
