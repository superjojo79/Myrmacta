package be.myrmacta.domain.promotion.artifact;

import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.InvalidBundleException;
import be.myrmacta.domain.promotion.model.PlanEntry;
import be.myrmacta.domain.promotion.port.out.ArtifactCodec;

/** TODO §117 — outils partagés par les artefacts de la tranche 2 (mêmes règles que l'import de la tranche 1). */
public final class PromotionSupport {

    public static final int MAX_REASON_CHARS = 400;
    /** Préfixe d'un avertissement dans la raison d'une ligne du plan (D-PROMO-ORPHAN) : toujours en tête, jamais tronqué. */
    public static final String WARNING = "WARNING: ";
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
        String head = cap(WARNING + warning);
        String reason = planned.reason().isEmpty() ? head : head + " " + cap(planned.reason());
        return new PlanEntry(planned.type(), planned.key(), planned.action(), reason, planned.secretsRequired(),
                planned.connectorName());
    }
}
