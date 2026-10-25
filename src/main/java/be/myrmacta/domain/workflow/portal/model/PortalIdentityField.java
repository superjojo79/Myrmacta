package be.myrmacta.domain.workflow.portal.model;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Champ d'identité d'une tâche externe à authentification forte (TODO §104, T2), déclaré par
 * {@code flowable:portalIdentityField="dossierField:<champ>"}. Même grammaire que {@link PortalRecipientSource},
 * réduite à {@code dossierField:} : le NRN vit dans le dossier, jamais dans une variable de process (N2). Lu par nom
 * littéral, jamais évalué (§69).
 */
public record PortalIdentityField(String fieldName) {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String PREFIX = "dossierField:";

    public PortalIdentityField {
        Objects.requireNonNull(fieldName, "fieldName");
        if (!IDENTIFIER.matcher(fieldName).matches()) {
            throw new IllegalArgumentException("portal identity field must be a simple identifier: " + fieldName);
        }
    }

    public static Optional<PortalIdentityField> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (!value.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String name = value.substring(PREFIX.length());
        return IDENTIFIER.matcher(name).matches() ? Optional.of(new PortalIdentityField(name)) : Optional.empty();
    }
}
