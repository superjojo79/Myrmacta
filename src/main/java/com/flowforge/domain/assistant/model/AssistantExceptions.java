package com.flowforge.domain.assistant.model;

import java.util.UUID;

/** Erreurs métier de l'assistant de conception. */
public final class AssistantExceptions {

    private AssistantExceptions() {}

    /** L'assistant n'est pas activé sur cette instance (aucune clé de modèle configurée). */
    public static class AssistantDisabledException extends RuntimeException {
        public AssistantDisabledException() {
            super("the design assistant is not enabled on this instance");
        }
    }

    /** Un plafond de consommation est atteint (tours, jetons, jour). */
    public static class AssistantLimitExceededException extends RuntimeException {
        public AssistantLimitExceededException(String message) {
            super(message);
        }
    }

    /** Le fournisseur du modèle est injoignable ou a refusé la requête (jamais de détail secret). */
    public static class ModelUnavailableException extends RuntimeException {
        public ModelUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        public ModelUnavailableException(String message) {
            super(message);
        }
    }

    /** Un tour est déjà en cours sur cette conversation (double envoi, suppression pendant un tour). */
    public static class AssistantBusyException extends RuntimeException {
        public AssistantBusyException() {
            super("the assistant is already working on this conversation: wait for its answer");
        }
    }

    public static class ActionNotFoundException extends RuntimeException {
        public ActionNotFoundException(UUID id) {
            super("proposed action not found: " + id);
        }
    }

    public static class ActionNotPendingException extends RuntimeException {
        public ActionNotPendingException(UUID id) {
            super("this proposed action was already handled: " + id);
        }
    }

    public static class ConversationNotFoundException extends RuntimeException {
        public ConversationNotFoundException(UUID id) {
            super("conversation not found: " + id);
        }
    }
}
