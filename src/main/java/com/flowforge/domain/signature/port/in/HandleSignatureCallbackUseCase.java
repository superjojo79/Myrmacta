package com.flowforge.domain.signature.port.in;

import com.flowforge.domain.signature.port.out.SignaturePort;

/**
 * Applique l'événement normalisé d'un fournisseur réel à sa demande (TODO §72). Idempotent : un
 * événement tardif ou rejoué sur une demande déjà close est ignoré et compté.
 */
public interface HandleSignatureCallbackUseCase {

    /** @return vrai si l'événement a fermé la demande, faux s'il a été ignoré */
    boolean apply(SignaturePort.SignatureCallbackEvent event);
}
