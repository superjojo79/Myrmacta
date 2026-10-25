package be.myrmacta.domain.assistant.port.out;

import be.myrmacta.domain.assistant.model.ModelExchange;

/** Appel d'un modèle de langage (le fournisseur est un détail d'infrastructure). */
public interface LanguageModelPort {

    /** @throws be.myrmacta.domain.assistant.model.AssistantExceptions.AssistantDisabledException si l'assistant est inactif
     *  @throws be.myrmacta.domain.assistant.model.AssistantExceptions.ModelUnavailableException si le fournisseur échoue */
    ModelExchange.Reply complete(ModelExchange.Request request);
}
