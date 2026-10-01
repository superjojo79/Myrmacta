package com.flowforge.domain.workflow.port.out;

/**
 * TODO §82 (D6) — politique de publication de l'instance (D-MCP-LC-PUBLISH = C) : lue au démarrage, jamais modifiable à
 * l'exécution. {@code false} (défaut) : seul un humain publie, depuis l'écran « Brouillons à publier ».
 */
public interface PublicationPolicy {

    boolean directPublicationAllowed();
}
