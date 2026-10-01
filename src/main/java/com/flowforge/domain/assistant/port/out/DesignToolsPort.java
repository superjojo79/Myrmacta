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

    class ToolInvocationException extends RuntimeException {
        public ToolInvocationException(String message) {
            super(message);
        }

        public ToolInvocationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
