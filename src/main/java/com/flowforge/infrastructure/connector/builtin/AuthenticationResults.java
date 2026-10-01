package com.flowforge.infrastructure.connector.builtin;

import java.util.Locale;

/**
 * TODO §87 (revue adversariale D3c/D3d, H-A) — lecture stricte d'un en-tête {@code Authentication-Results} (RFC 8601) pour décider si
 * l'expéditeur d'un e-mail est <b>attesté</b>. Trois exigences, car chercher « dkim=pass » dans le texte ne prouve rien :
 * <ol>
 *   <li>l'en-tête doit venir de <em>notre</em> serveur : son {@code authserv-id} (premier jeton) doit être celui configuré ;</li>
 *   <li>le résultat est lu sur le <em>premier jeton</em> de chaque segment ({@code méthode=résultat}), jamais dans les propriétés
 *       (un MAIL FROM {@code dkim=pass@x} s'y recopie tel quel) ;</li>
 *   <li>l'attestation doit être <em>alignée</em> sur l'adresse « De » : {@code dmarc=pass} (qui l'est par construction), ou
 *       {@code dkim=pass} avec {@code header.d}, ou {@code spf=pass} avec {@code smtp.mailfrom}, du même domaine que « De »
 *       (sous-domaine accepté, alignement souple).</li>
 * </ol>
 */
final class AuthenticationResults {

    private AuthenticationResults() {}

    static boolean attests(String header, String trustedAuthservId, String fromAddress) {
        if (header == null || header.isBlank() || trustedAuthservId == null || trustedAuthservId.isBlank()) {
            return false;
        }
        String fromDomain = domainOf(fromAddress);
        if (fromDomain == null) {
            return false;
        }
        String[] segments = header.split(";");
        String authservId = segments[0].strip().split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (!authservId.equals(trustedAuthservId.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        boolean attested = false;
        for (int i = 1; i < segments.length; i++) {
            String[] tokens = segments[i].strip().split("\\s+");
            if (tokens.length == 0 || tokens[0].isEmpty()) {
                continue;
            }
            int eq = tokens[0].indexOf('=');
            if (eq < 1) {
                continue;
            }
            String method = tokens[0].substring(0, eq).toLowerCase(Locale.ROOT);
            String result = tokens[0].substring(eq + 1).toLowerCase(Locale.ROOT);
            if (!method.equals("dkim") && !method.equals("spf") && !method.equals("dmarc")) {
                continue;
            }
            if (!result.equals("pass")) {
                if (result.equals("fail") || result.equals("softfail") || result.equals("permerror") || result.equals("temperror")) {
                    return false; // un échec annule toute attestation
                }
                continue;
            }
            switch (method) {
                case "dmarc" -> attested = true;
                case "dkim" -> attested |= aligned(property(tokens, "header.d"), fromDomain);
                case "spf" -> attested |= aligned(domainOf(property(tokens, "smtp.mailfrom")), fromDomain)
                        || aligned(property(tokens, "smtp.mailfrom"), fromDomain);
                default -> { }
            }
        }
        return attested;
    }

    private static String property(String[] tokens, String name) {
        for (int i = 1; i < tokens.length; i++) {
            if (tokens[i].toLowerCase(Locale.ROOT).startsWith(name + "=")) {
                return tokens[i].substring(name.length() + 1).replace("\"", "");
            }
        }
        return null;
    }

    /** Alignement souple : même domaine, ou l'un est un sous-domaine de l'autre (jamais un simple suffixe de texte). */
    static boolean aligned(String signingDomain, String fromDomain) {
        if (signingDomain == null || signingDomain.isBlank()) {
            return false;
        }
        String a = signingDomain.strip().toLowerCase(Locale.ROOT);
        String b = fromDomain.toLowerCase(Locale.ROOT);
        return a.equals(b) || a.endsWith("." + b) || b.endsWith("." + a);
    }

    private static String domainOf(String address) {
        if (address == null) {
            return null;
        }
        int at = address.lastIndexOf('@');
        if (at < 0 || at == address.length() - 1) {
            return address.contains(".") && !address.contains(" ") ? address.toLowerCase(Locale.ROOT) : null;
        }
        return address.substring(at + 1).strip().toLowerCase(Locale.ROOT);
    }
}
