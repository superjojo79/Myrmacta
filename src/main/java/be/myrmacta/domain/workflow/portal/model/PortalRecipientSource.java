package be.myrmacta.domain.workflow.portal.model;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Source de l'adresse du destinataire d'une tâche externe, déclarée dans le BPMN par
 * {@code flowable:portalRecipient} : {@code dossierField:<clé>} ou {@code variable:<nom>}. Le nom
 * est un identifiant simple, toujours lu <strong>par nom littéral</strong> (jamais évalué en UEL —
 * TODO §69). Même grammaire pour le destinataire de la brique « Notifier » ({@code flowable:notificationTo},
 * TODO §93).
 */
public record PortalRecipientSource(Kind kind, String name) {

    public enum Kind {
        DOSSIER_FIELD,
        PROCESS_VARIABLE
    }

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String DOSSIER_FIELD_PREFIX = "dossierField:";
    private static final String VARIABLE_PREFIX = "variable:";

    public PortalRecipientSource {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        if (!IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("portal recipient name must be a simple identifier: " + name);
        }
    }

    /** Vrai si {@code raw} se présente comme une source ({@code dossierField:} ou {@code variable:}), bien formée ou non. */
    public static boolean hasSourcePrefix(String raw) {
        if (raw == null) {
            return false;
        }
        String value = raw.trim();
        return value.startsWith(DOSSIER_FIELD_PREFIX) || value.startsWith(VARIABLE_PREFIX);
    }

    /** Vide si {@code raw} n'a pas la forme attendue. */
    public static Optional<PortalRecipientSource> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim();
        Kind kind;
        String name;
        if (value.startsWith(DOSSIER_FIELD_PREFIX)) {
            kind = Kind.DOSSIER_FIELD;
            name = value.substring(DOSSIER_FIELD_PREFIX.length());
        } else if (value.startsWith(VARIABLE_PREFIX)) {
            kind = Kind.PROCESS_VARIABLE;
            name = value.substring(VARIABLE_PREFIX.length());
        } else {
            return Optional.empty();
        }
        if (!IDENTIFIER.matcher(name).matches()) {
            return Optional.empty();
        }
        return Optional.of(new PortalRecipientSource(kind, name));
    }
}
