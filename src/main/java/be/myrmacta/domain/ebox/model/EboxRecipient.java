package be.myrmacta.domain.ebox.model;

import java.util.regex.Pattern;

/**
 * TODO §155 (D-EBOX-SEND-MODEL) — destinataire d'un envoi : une entreprise (numéro BCE) ou un citoyen (numéro de registre national,
 * NISS). Le NISS n'apparaît jamais dans {@link #toString()} : un destinataire peut finir dans un message d'erreur ou un journal.
 */
public sealed interface EboxRecipient {

    EboxRoute route();

    /** Entreprise : dix chiffres commençant par 0 ou 1 (points et espaces retirés). */
    record Enterprise(String enterpriseNumber) implements EboxRecipient {

        private static final Pattern BCE = Pattern.compile("[01]\\d{9}");

        public Enterprise {
            String value = enterpriseNumber == null ? "" : enterpriseNumber.replace(".", "").replace(" ", "");
            if (!BCE.matcher(value).matches()) {
                throw new IllegalArgumentException("enterpriseNumber must be a ten-digit CBE number starting with 0 or 1");
            }
            enterpriseNumber = value;
        }

        @Override
        public EboxRoute route() {
            return EboxRoute.ENTERPRISE;
        }
    }

    /** Citoyen : NISS de onze chiffres, clé de contrôle vérifiée (règle d'avant et d'après 2000). */
    record Citizen(String ssin) implements EboxRecipient {

        private static final Pattern SSIN = Pattern.compile("\\d{11}");

        public Citizen {
            String value = ssin == null ? "" : ssin.replaceAll("[.\\- ]", "");
            if (!SSIN.matcher(value).matches() || !checksumMatches(value)) {
                throw new IllegalArgumentException("ssin must be a valid eleven-digit national number");
            }
            ssin = value;
        }

        @Override
        public EboxRoute route() {
            return EboxRoute.CITIZEN;
        }

        @Override
        public String toString() {
            return "Citizen[ssin=***********]";
        }

        private static boolean checksumMatches(String value) {
            long base = Long.parseLong(value.substring(0, 9));
            int check = Integer.parseInt(value.substring(9));
            return 97 - base % 97 == check || 97 - (2_000_000_000L + base) % 97 == check;
        }
    }
}
