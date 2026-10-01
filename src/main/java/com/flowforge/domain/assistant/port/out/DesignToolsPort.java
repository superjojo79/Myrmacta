package com.flowforge.domain.assistant.port.out;

import com.flowforge.domain.assistant.model.ToolSpec;
import com.flowforge.domain.dossier.model.AccessContext;
import java.util.List;

/**
 * Outils de conception offerts à l'assistant (le catalogue MCP en mode conception, sans aucune donnée de dossier). L'exécution
 * se fait avec l'identité de l'utilisateur de la session, jamais avec un jeton de service.
 */
public interface DesignToolsPort {

    List<ToolSpec> specs();

    /**
     * @return le résultat sérialisé en JSON
     * @throws ToolInvocationException l'outil a refusé ou échoué : le message est renvoyé au modèle (qui peut corriger)
     */
    String invoke(String toolName, String argumentsJson, AccessContext.User actor);

    /** Outil qui écrit tout de suite (pas de brouillon) : l'assistant le propose, l'humain l'applique. */
    boolean needsConfirmation(String toolName);

    /** Valide les arguments d'un outil à confirmation <b>sans rien écrire</b> (force le mode à blanc). */
    String preview(String toolName, String argumentsJson, AccessContext.User actor);

    /** Exécute réellement une écriture confirmée par l'humain, avec son identité. */
    String invokeConfirmed(String toolName, String argumentsJson, AccessContext.User actor);

    class ToolInvocationException extends RuntimeException {
        public ToolInvocationException(String message) {
            super(message);
        }

        public ToolInvocationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
