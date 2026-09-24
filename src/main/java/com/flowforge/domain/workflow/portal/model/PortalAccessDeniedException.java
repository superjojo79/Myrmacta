package com.flowforge.domain.workflow.portal.model;

/**
 * Refus d'accès portail — <strong>une seule</strong> exception pour tous les cas (jeton absent,
 * inconnu, expiré, révoqué, déjà utilisé, tâche disparue) : la réponse HTTP doit être identique
 * pour ne pas servir d'oracle (US-3, critère de succès « pas d'oracle »). Le motif réel n'est
 * destiné qu'aux logs serveur.
 */
public class PortalAccessDeniedException extends RuntimeException {

    private final String internalReason;

    public PortalAccessDeniedException(String internalReason) {
        super("portal access denied");
        this.internalReason = internalReason;
    }

    public String internalReason() {
        return internalReason;
    }
}
