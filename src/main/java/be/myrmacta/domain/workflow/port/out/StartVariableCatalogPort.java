package be.myrmacta.domain.workflow.port.out;

import be.myrmacta.domain.workflow.model.StartVariableDeclaration;
import java.util.List;

/**
 * TODO §111 (D-START-DECLARED) — variables de démarrage lues sur la dernière version déployée du processus.
 * Vide si le processus n'est pas déployé ou n'en déclare aucune.
 */
public interface StartVariableCatalogPort {

    List<StartVariableDeclaration> findDeclared(String processDefinitionKey);
}
