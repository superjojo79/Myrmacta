package com.flowforge.domain.workflow.palette;

public enum ParameterType {
    TEXT,
    ENUM,
    ENUM_MULTI,
    INTEGER,
    /** Clé ou UUID d'une ressource du tenant, proposée depuis une source nommée. */
    REFERENCE,
    /** {@code dossierField:<nom>} ou {@code variable:<nom>}. */
    SOURCE_REF,
    /** Signataires répétés (bloc de signature). */
    SIGNERS
}
