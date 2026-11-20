package be.myrmacta.domain.ebox.model;

import java.time.Instant;
import java.util.Objects;

/**
 * TODO §155 (D-EBOX-SEND-CONSENT) — état constaté juste avant l'envoi, conservé au journal d'audit avec l'envoi.
 *
 * <ul>
 *   <li>citoyen ({@code citizenEboxConsents/search}, federation 1.4) : {@link Level#CONSENTED}, {@link Level#NO_CONSENT}, ou
 *       {@link Level#NO_EBOX} si le citoyen n'est pas connu de l'eBox ;</li>
 *   <li>entreprise ({@code eboxPreferences/search}) : il n'y a pas de consentement à vérifier (la spec le dit), seulement la
 *       préférence {@code exclusivelyEbox} : {@link Level#EXCLUSIVE_EBOX}, {@link Level#EBOX_NOT_EXCLUSIVE}, ou
 *       {@link Level#NO_EBOX} si l'entreprise est absente du résultat.</li>
 * </ul>
 *
 * @param source service qui a répondu (ex. {@code federation-1.4}, {@code citizen-stub}) : le journal dit d'où vient l'état
 */
public record EboxConsent(Level level, Instant checkedAt, String source) {

    public enum Level {
        /** Entreprise qui a choisi l'eBox comme canal exclusif : plus de copie papier. */
        EXCLUSIVE_EBOX(EboxRoute.ENTERPRISE),
        /** Entreprise dotée d'une eBox, sans exclusivité. */
        EBOX_NOT_EXCLUSIVE(EboxRoute.ENTERPRISE),
        /** Citoyen qui a accepté les conditions d'utilisation de l'eBox. */
        CONSENTED(EboxRoute.CITIZEN),
        /** Citoyen connu, sans consentement. */
        NO_CONSENT(EboxRoute.CITIZEN),
        /** Destinataire sans eBox (ou inconnu du service). */
        NO_EBOX(null);

        private final EboxRoute route;

        Level(EboxRoute route) {
            this.route = route;
        }

        /** Vrai si cet état peut être constaté pour cette voie. */
        public boolean appliesTo(EboxRoute candidate) {
            return route == null || route == candidate;
        }
    }

    public EboxConsent {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(checkedAt, "checkedAt");
        source = EboxTexts.line(source, "source", 50);
    }
}
