package be.myrmacta.domain.precheck.port.out;

import be.myrmacta.domain.precheck.model.ModelExchange;

/**
 * TODO §106 (lot A, C1) — modèle de langage de la pré-vérification : un appel, sans outils, réponse au format JSON.
 *
 * @throws be.myrmacta.domain.precheck.model.PrecheckExceptions.PrecheckModelUnavailableException fournisseur en échec
 */
public interface PrecheckModelPort {

    ModelExchange.Reply complete(ModelExchange.Request request);
}
