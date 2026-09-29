package com.flowforge.domain.signature.port.out;

/**
 * Disponibilité de la fonctionnalité et bornes tenant (TODO §72). {@code providerActive} est faux
 * quand {@code app.signature.provider=none} : un bloc signature est alors refusé au déploiement.
 */
public interface SignatureAvailabilityPort {

    boolean providerActive();

    /** Les signataires {@code EXTERNAL} passent par le portail (D-SIGNATURE-EXTERNAL-NEEDS-PORTAL). */
    boolean portalEnabled();

    int maxSigners();

    int defaultDeadlineDays();

    int maxDeadlineDays();

    long maxDocumentBytes();
}
