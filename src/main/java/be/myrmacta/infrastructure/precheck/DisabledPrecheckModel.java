package be.myrmacta.infrastructure.precheck;

import be.myrmacta.domain.precheck.model.ModelExchange;
import be.myrmacta.domain.precheck.model.PrecheckExceptions.PrecheckDisabledException;
import be.myrmacta.domain.precheck.port.out.PrecheckModelPort;

/** TODO §106 (P8) — pré-vérification désactivée : aucun appel sortant, jamais. */
final class DisabledPrecheckModel implements PrecheckModelPort {

    @Override
    public ModelExchange.Reply complete(ModelExchange.Request request) {
        throw new PrecheckDisabledException();
    }
}
