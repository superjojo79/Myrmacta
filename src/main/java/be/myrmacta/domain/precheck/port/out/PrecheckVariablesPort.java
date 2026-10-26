package be.myrmacta.domain.precheck.port.out;

import java.util.Map;
import java.util.UUID;

/**
 * TODO §106 (R6, R7) — variables du workflow actif du dossier, pour les règles d'une relance hors workflow (bouton,
 * MCP) ; vide si aucun workflow n'est actif (règles alors « non évaluées »).
 */
public interface PrecheckVariablesPort {

    Map<String, Object> currentVariables(UUID dossierId);
}
