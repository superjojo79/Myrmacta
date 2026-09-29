package com.flowforge.domain.signature.port.out;

import com.flowforge.domain.signature.model.SignatureOutcome;

/** Compteurs du lot signature (TODO §72) : aucun libellé ne porte d'adresse, de jeton ni d'identité. */
public interface SignatureMetricsPort {

    void requestCreated(String providerCode, boolean simulated);

    void outcome(SignatureOutcome outcome);

    void dispatchRetry();

    void dispatchGaveUp();

    void callbackRejected(String reason);
}
