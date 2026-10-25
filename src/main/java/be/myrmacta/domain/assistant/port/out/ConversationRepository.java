package be.myrmacta.domain.assistant.port.out;

import be.myrmacta.domain.assistant.model.AssistantAction;
import be.myrmacta.domain.assistant.model.ChatMessage;
import be.myrmacta.domain.assistant.model.Conversation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository {

    /** Insère une nouvelle conversation (jamais une fusion : une conversation supprimée ne ressuscite pas). */
    Conversation create(Conversation conversation);

    /** Met à jour une conversation existante ; {@code false} si elle a disparu (supprimée pendant un tour). */
    boolean update(Conversation conversation);

    /**
     * Prend le droit d'exécuter un tour sur cette conversation (un seul à la fois ; bail borné pour qu'un arrêt brutal ne la
     * bloque pas pour toujours).
     */
    boolean tryLock(UUID id, Instant now, Instant until);

    void unlock(UUID id);

    AssistantAction saveAction(AssistantAction action);

    java.util.Optional<AssistantAction> findAction(UUID id, UUID conversationId);

    List<AssistantAction> actions(UUID conversationId);

    /** Transition atomique {@code expected → next} ; {@code false} si l'action n'était plus dans l'état attendu. */
    boolean transitionAction(UUID id, AssistantAction.Status expected, AssistantAction.Status next, String outcome, Instant at);

    ChatMessage saveMessage(ChatMessage message);

    /** Une conversation n'est visible que de son propriétaire (jamais d'un autre administrateur). */
    Optional<Conversation> findByIdAndOwner(UUID id, String tenantId, String ownerId);

    List<Conversation> listByOwner(String tenantId, String ownerId, int limit);

    List<ChatMessage> messages(UUID conversationId);

    /** Jetons consommés par le tenant ce jour-là (compteur propre, que la suppression d'une conversation n'efface pas). */
    long tokensOn(String tenantId, LocalDate day);

    void addTokens(String tenantId, LocalDate day, long tokens);

    /** @throws be.myrmacta.domain.assistant.model.AssistantExceptions.AssistantBusyException si un tour est en cours */
    boolean deleteByIdAndOwner(UUID id, String tenantId, String ownerId, Instant now);

    int deleteOlderThan(String tenantId, Instant cutoff);
}
