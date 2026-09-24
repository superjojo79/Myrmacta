package com.flowforge.domain.workflow.portal.model;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validation minimale et masquage d'une adresse de destinataire externe. Volontairement simple
 * (pas de RFC 5322 complète) : une adresse refusée ici produit un lien {@code NOT_SENT} que
 * l'instructeur corrige (D-PORTAL-NO-ADDRESS), jamais une erreur du process.
 */
public final class PortalEmails {

    private static final int MAX_LENGTH = 320;
    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+'-]{1,64}@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+");

    private PortalEmails() {}

    /** Adresse normalisée (trim), ou vide si absente/invalide. */
    public static Optional<String> normalize(Object raw) {
        if (!(raw instanceof String value)) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH || !EMAIL.matcher(trimmed).matches()) {
            return Optional.empty();
        }
        return Optional.of(trimmed);
    }

    /** {@code alice@example.org} → {@code a***@example.org} ; jamais l'adresse complète dans un log. */
    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "<none>";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
