package be.myrmacta.infrastructure.assistant;

import be.myrmacta.domain.assistant.model.AssistantExceptions.AssistantDisabledException;
import be.myrmacta.domain.assistant.model.ModelExchange;
import be.myrmacta.domain.assistant.port.out.LanguageModelPort;

/** Modèle d'une instance où l'assistant n'est pas activé : aucune requête sortante n'est jamais faite. */
public final class DisabledLanguageModel implements LanguageModelPort {

    @Override
    public ModelExchange.Reply complete(ModelExchange.Request request) {
        throw new AssistantDisabledException();
    }
}
