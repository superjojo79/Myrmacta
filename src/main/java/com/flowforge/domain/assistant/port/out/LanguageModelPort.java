package com.flowforge.domain.assistant.port.out;

import com.flowforge.domain.assistant.model.ModelExchange;

/** Appel d'un modèle de langage (le fournisseur est un détail d'infrastructure). */
public interface LanguageModelPort {

    /** @throws com.flowforge.domain.assistant.model.AssistantExceptions.AssistantDisabledException si l'assistant est inactif
     *  @throws com.flowforge.domain.assistant.model.AssistantExceptions.ModelUnavailableException si le fournisseur échoue */
    ModelExchange.Reply complete(ModelExchange.Request request);
}
