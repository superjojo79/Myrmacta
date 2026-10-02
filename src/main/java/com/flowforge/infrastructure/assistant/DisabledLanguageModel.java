package com.flowforge.infrastructure.assistant;

import com.flowforge.domain.assistant.model.AssistantExceptions.AssistantDisabledException;
import com.flowforge.domain.assistant.model.ModelExchange;
import com.flowforge.domain.assistant.port.out.LanguageModelPort;

/** Modèle d'une instance où l'assistant n'est pas activé : aucune requête sortante n'est jamais faite. */
public final class DisabledLanguageModel implements LanguageModelPort {

    @Override
    public ModelExchange.Reply complete(ModelExchange.Request request) {
        throw new AssistantDisabledException();
    }
}
