package com.flowforge.domain.workflow.palette;

import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO §102 (issue #184, lot B, R2) — forme du paramètre {@code searchCriteria} de la brique « Rechercher des dossiers » : une
 * ligne {@code champ = source} par critère d'égalité, la source étant {@code dossierField:<champ>} (dossier courant) ou
 * {@code variable:<nom>} (variable de process). Même grammaire de source que {@code notificationTo} et
 * {@code portalRecipient}, toujours lue par nom littéral. Le caractère cherchable des champs dépend du type du dossier, connu
 * seulement à l'exécution (D-SEARCH-BLOCK-SCOPE).
 */
public final class DossierSearchSpec {

    public static final int MAX_CRITERIA = 3;

    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final Pattern LINE = Pattern.compile("^([^=]*?)\\s*=(?!=)\\s*(.*)$");

    /** Critère : champ cherché dans les autres dossiers, et d'où vient la valeur cherchée. */
    public record Criterion(String field, PortalRecipientSource source) {
        public Criterion {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(source, "source");
        }
    }

    private DossierSearchSpec() {}

    /** @throws InvalidBpmnException forme invalide (aucun ou trop de critères, doublon, nom ou source hors règles) */
    public static List<Criterion> parse(String raw, String owner) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidBpmnException(owner + " must list at least one 'field = dossierField:<name>' line");
        }
        List<Criterion> criteria = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : raw.split("\\R")) {
            String stripped = line.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            Matcher m = LINE.matcher(stripped);
            if (!m.matches()) {
                throw new InvalidBpmnException(owner + ": each line must be 'field = dossierField:<name>' or "
                        + "'field = variable:<name>'");
            }
            String field = m.group(1).strip();
            if (!FIELD_NAME.matcher(field).matches()) {
                throw new InvalidBpmnException(owner + ": '" + abbreviate(field)
                        + "' is not a valid field name (letters, digits, _; starts with a letter)");
            }
            PortalRecipientSource source = PortalRecipientSource.parse(m.group(2).strip())
                    .orElseThrow(() -> new InvalidBpmnException(owner + ": the value of '" + field
                            + "' must be dossierField:<name> or variable:<name>"));
            if (!seen.add(field)) {
                throw new InvalidBpmnException(owner + ": field '" + field + "' is searched twice");
            }
            criteria.add(new Criterion(field, source));
            if (criteria.size() > MAX_CRITERIA) {
                throw new InvalidBpmnException(owner + ": at most " + MAX_CRITERIA + " criteria");
            }
        }
        if (criteria.isEmpty()) {
            throw new InvalidBpmnException(owner + " must list at least one 'field = dossierField:<name>' line");
        }
        return List.copyOf(criteria);
    }

    private static String abbreviate(String value) {
        return value.length() <= 30 ? value : value.substring(0, 30) + "…";
    }
}
