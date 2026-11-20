package be.myrmacta.domain.ebox.model;

import be.myrmacta.domain.ebox.model.EboxConsent.Level;
import java.util.Objects;

/**
 * TODO §155 (D-EBOX-SEND-CONSENT) — ce que l'envoi fait, selon la voie et l'état constaté. Le poids du consentement diffère :
 *
 * <ul>
 *   <li>citoyen : sans consentement préalable (loi du 27/02/2019, art. 6), rien ne part dans l'eBox, tout passe par la voie de
 *       secours ;</li>
 *   <li>entreprise : pas de consentement ; l'eBox reçoit le message, et la voie de secours en porte une copie tant que
 *       l'entreprise n'a pas choisi l'eBox comme canal exclusif (hypothèse H1 du registre §155, à confirmer) ;</li>
 *   <li>pas d'eBox : voie de secours seule.</li>
 * </ul>
 */
public enum EboxDelivery {
    EBOX,
    EBOX_AND_FALLBACK,
    FALLBACK;

    public boolean publishes() {
        return this != FALLBACK;
    }

    public boolean needsFallback() {
        return this != EBOX;
    }

    public static EboxDelivery decide(EboxRoute route, Level level) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(level, "level");
        if (!level.appliesTo(route)) {
            throw new IllegalArgumentException("consent level " + level + " cannot be observed for route " + route);
        }
        return switch (level) {
            case CONSENTED, EXCLUSIVE_EBOX -> EBOX;
            case EBOX_NOT_EXCLUSIVE -> EBOX_AND_FALLBACK;
            case NO_CONSENT, NO_EBOX -> FALLBACK;
        };
    }
}
