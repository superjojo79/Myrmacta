package be.myrmacta.domain.promotion.artifact;

import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.InvalidBundleException;
import be.myrmacta.domain.promotion.model.PlanEntry;
import be.myrmacta.domain.promotion.port.out.ArtifactCodec;
import java.util.ArrayList;
import java.util.List;

/** TODO §117 — outils partagés par les artefacts de la tranche 2 (mêmes règles que l'import de la tranche 1). */
public final class PromotionSupport {

    public static final int MAX_REASON_CHARS = 400;
    /** Préfixe d'un avertissement dans la raison d'une ligne du plan (D-PROMO-ORPHAN) : toujours en tête, jamais tronqué. */
    public static final String WARNING = "WARNING: ";
    /**
     * Sépare un avertissement de la suite de la raison : la ligne du rapport d'application reprend les avertissements du plan
     * sans le reste de sa raison (issue #285).
     */
    public static final String AFTER_WARNING = " | ";
    /** Raison d'une configuration de workflow jointe à un brouillon BPMN : elle n'existera qu'à sa publication (D-PROMO-WFCONFIG). */
    public static final String JOINED_TO_DRAFT = "joined to the BPMN draft";

    private PromotionSupport() {}

    public static <T> T decode(ArtifactCodec codec, BundleEntry entry, Class<T> type) {
        try {
            return codec.decode(entry.content(), type);
        } catch (InvalidBundleException e) {
            throw new InvalidBundleException(entry.type() + " '" + entry.key() + "': " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new InvalidBundleException(entry.type() + " '" + entry.key() + "' is unreadable: " + e.getMessage(), e);
        }
    }

    public static void requireSameKey(BundleEntry entry, String docIdentity) {
        if (!entry.key().equals(docIdentity)) {
            throw new InvalidBundleException(entry.type() + " file '" + entry.key() + "' describes '" + docIdentity
                    + "': the file name and its content must agree");
        }
    }

    public static String cap(String text) {
        String t = text == null ? "" : text;
        return t.length() > MAX_REASON_CHARS ? t.substring(0, MAX_REASON_CHARS) + "…" : t;
    }

    /** Ajoute un avertissement en tête de la raison, sans changer l'action : seule la suite peut être tronquée. */
    public static PlanEntry warn(PlanEntry planned, String warning) {
        if (warning == null || warning.isBlank()) {
            return planned;
        }
        String head = cap(WARNING + warning.strip());
        String reason = planned.reason().isEmpty() ? head : head + AFTER_WARNING + cap(planned.reason());
        return new PlanEntry(planned.type(), planned.key(), planned.action(), reason, planned.secretsRequired(),
                planned.connectorName());
    }

    /** Les avertissements en tête d'une raison du plan, sans la suite ; vide s'il n'y en a pas. */
    public static String warningsOf(String reason) {
        if (reason == null || !reason.startsWith(WARNING)) {
            return "";
        }
        List<String> warnings = new ArrayList<>();
        for (String part : reason.split(java.util.regex.Pattern.quote(AFTER_WARNING), -1)) {
            if (!part.startsWith(WARNING)) {
                break;
            }
            warnings.add(part);
        }
        return String.join(AFTER_WARNING, warnings);
    }

    /** Le message d'une ligne du rapport, précédé des avertissements de la ligne du plan (issue #285). */
    public static String withWarningsOf(PlanEntry planned, String message) {
        String warnings = warningsOf(planned.reason());
        if (warnings.isEmpty()) {
            return message;
        }
        return message == null || message.isEmpty() ? warnings : warnings + AFTER_WARNING + message;
    }
}
