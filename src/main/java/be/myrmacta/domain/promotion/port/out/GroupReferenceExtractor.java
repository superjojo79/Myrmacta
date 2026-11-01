package be.myrmacta.domain.promotion.port.out;

import java.util.Set;

/**
 * TODO §117 (D-PROMO-ORPHAN) — codes de groupes d'utilisateurs nommés littéralement par un processus ou une table de décision, pour
 * avertir à l'import d'un groupe inconnu de la cible et absent du bundle. Un document illisible donne un ensemble vide : sa
 * validation, elle, le bloque.
 */
public interface GroupReferenceExtractor {

    /** {@code candidateGroups} littéraux des tâches et attribut {@code groupeTri} de « Router le courrier ». */
    Set<String> fromBpmn(byte[] bpmnXml);

    /** Valeurs littérales de la sortie {@code routageService} (groupe destinataire du routage du courrier). */
    Set<String> fromDmn(byte[] dmnXml);
}
