package be.myrmacta.domain.promotion.usecase;

import be.myrmacta.domain.promotion.port.out.GroupReferenceExtractor;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * TODO §117 (D-PROMO-ORPHAN) — un processus ou une table de décision qui nomme un groupe absent de la cible et du bundle reste
 * appliqué : l'exécution se dégrade sans erreur (courrier à la file de tri, tâche libre). Le plan l'avertit.
 */
public final class GroupReferenceCheck {

    private static final int MAX_LISTED = 10;

    private final GroupReferenceExtractor extractor;

    public GroupReferenceCheck(GroupReferenceExtractor extractor) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
    }

    /** @param known groupes de la cible et du bundle (non bloqués) */
    String bpmnWarning(byte[] xml, Set<String> known) {
        Set<String> missing = missing(extractor.fromBpmn(xml), known);
        return missing.isEmpty()
                ? ""
                : "names user group(s) unknown here and absent from the bundle: " + list(missing)
                        + " (their tasks stay unassigned, mail goes to the sorting queue).";
    }

    String dmnWarning(byte[] xml, Set<String> known) {
        Set<String> missing = missing(extractor.fromDmn(xml), known);
        return missing.isEmpty()
                ? ""
                : "routes to user group(s) unknown here and absent from the bundle: " + list(missing)
                        + " (such mail goes to the sorting queue).";
    }

    private static Set<String> missing(Set<String> referenced, Set<String> known) {
        Set<String> missing = new TreeSet<>(referenced);
        missing.removeAll(known);
        return missing;
    }

    private static String list(Set<String> codes) {
        return codes.stream().limit(MAX_LISTED).toList() + (codes.size() > MAX_LISTED ? " and " + (codes.size() - MAX_LISTED) + " more" : "");
    }
}
