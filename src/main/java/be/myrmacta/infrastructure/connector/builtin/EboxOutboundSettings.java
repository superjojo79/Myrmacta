package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * TODO §155 (D-EBOX-SEND-CHANNELS) — configuration d'un compte expéditeur eBox Enterprise, lue dans une instance de connecteur de
 * type {@value #TYPE} (secrets chiffrés au repos comme ceux des autres connecteurs). Le type de connecteur et son écran arrivent
 * au lot 2 ; ce lot lit et valide sa configuration.
 *
 * <ul>
 *   <li>{@code publicationUrl} : registre de messages de l'ONSS (e-Box REST 3.8, {@code POST /ebox/messages}) ;</li>
 *   <li>{@code federationUrl} : e-Box Federation 1.4 ({@code eboxPreferences/search}) ;</li>
 *   <li>{@code registeredMailUrl} : Registered Mail 1.1, facultatif (sans lui, un recommandé est refusé) ;</li>
 *   <li>{@code onBehalfOf} : numéro BCE de la commune représentée (mandat Mahis), facultatif ;</li>
 *   <li>OAuth v5 : {@code oauthTokenUrl}, {@code oauthClientId}, {@code oauthPrivateKey}, {@code oauthCertThumbprint},
 *       {@code oauthScope} (publication) et {@code registeredMailScope}.</li>
 * </ul>
 *
 * Chaque URL est en https et son hôte finit par un suffixe autorisé (D-EBOX-REGISTRY-HOSTS, même règle qu'en lecture) : le jeton
 * ne part nulle part ailleurs.
 */
record EboxOutboundSettings(
        String publicationUrl,
        String federationUrl,
        String registeredMailUrl,
        String onBehalfOf,
        String clientId,
        ConnectorInstanceData publicationOauth,
        ConnectorInstanceData registeredMailOauth) {

    static final String TYPE = "ebox-outbound";
    static final String DEFAULT_SCOPE = "scope:document:management:consult:ws-eboxrestentreprise:publicationsender";
    static final String DEFAULT_REGISTERED_MAIL_SCOPE = "scope:ksz-bcss:registered:epost-rest:registeredmailsender";
    static final String DEFAULT_SUFFIXES = "socialsecurity.be,fgov.be,belgium.be";

    /** Même borne que la colonne {@code client_id} du journal d'audit. */
    static final int MAX_CLIENT_ID = 200;
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final Pattern BCE = Pattern.compile("[01]\\d{9}");
    private static final Pattern HOST_SUFFIX = Pattern.compile("[a-z0-9-]+(\\.[a-z0-9-]+)+");

    static EboxOutboundSettings of(ConnectorInstanceData instance, OutboundUrlGuard guard) {
        if (!TYPE.equals(instance.connectorType())) {
            throw new IllegalStateException("connector instance is not an eBox sender (" + TYPE + ")");
        }
        if (!instance.enabled()) {
            throw new IllegalStateException("the eBox sender connector instance is disabled");
        }
        Map<String, Object> config = instance.config();
        List<String> suffixes = suffixes(config);
        String publication = url(config, "publicationUrl", suffixes, guard, true);
        String federation = url(config, "federationUrl", suffixes, guard, true);
        String registered = url(config, "registeredMailUrl", suffixes, guard, false);
        url(config, "oauthTokenUrl", suffixes, guard, true);
        String onBehalfOf = text(config, "onBehalfOf").replace(".", "").replace(" ", "");
        if (!onBehalfOf.isEmpty() && !BCE.matcher(onBehalfOf).matches()) {
            throw new IllegalStateException("onBehalfOf must be a ten-digit CBE number starting with 0 or 1");
        }
        String clientId = text(config, "oauthClientId");
        if (clientId.isEmpty() || clientId.length() > MAX_CLIENT_ID || CONTROL.matcher(clientId).find()) {
            throw new IllegalStateException("oauthClientId is required, at most " + MAX_CLIENT_ID + " characters, without control "
                    + "characters");
        }
        Object method = config.get("oauthClientAuthMethod");
        if (method != null && !OAuth2TokenProvider.METHOD_JWT.equals(String.valueOf(method))) {
            throw new IllegalStateException("eBox only supports oauthClientAuthMethod " + OAuth2TokenProvider.METHOD_JWT);
        }
        return new EboxOutboundSettings(publication, federation, registered, onBehalfOf.isEmpty() ? null : onBehalfOf,
                clientId, oauth(instance, text(config, "oauthScope"), DEFAULT_SCOPE),
                oauth(instance, text(config, "registeredMailScope"), DEFAULT_REGISTERED_MAIL_SCOPE));
    }

    /** Une configuration OAuth par scope : le cache de jetons les distingue par empreinte de configuration. */
    private static ConnectorInstanceData oauth(ConnectorInstanceData instance, String scope, String fallback) {
        Map<String, Object> copy = new HashMap<>(instance.config());
        copy.put("oauthClientAuthMethod", OAuth2TokenProvider.METHOD_JWT);
        copy.put("oauthScope", scope.isBlank() ? fallback : scope);
        return new ConnectorInstanceData(instance.id(), instance.tenantId(), instance.name(), instance.connectorType(),
                instance.enabled(), copy);
    }

    private static String url(Map<String, Object> config, String key, List<String> suffixes, OutboundUrlGuard guard,
            boolean required) {
        String value = text(config, key).replaceAll("/+$", "");
        if (value.isEmpty()) {
            if (required) {
                throw new IllegalStateException(key + " is required");
            }
            return null;
        }
        if (!allowed(value, suffixes, guard)) {
            throw new IllegalStateException(key + " must be an https URL on an allowed domain (allowedHostSuffixes)");
        }
        return value;
    }

    private static boolean allowed(String url, List<String> suffixes, OutboundUrlGuard guard) {
        URI uri;
        try {
            uri = URI.create(url);
            guard.assertSafe(url);
        } catch (IllegalArgumentException refused) {
            return false;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null || host.isEmpty()) {
            return false;
        }
        if (guard.isLocalLoopback(host)) {
            return true;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        return suffixes.stream().anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
    }

    private static List<String> suffixes(Map<String, Object> config) {
        String raw = text(config, "allowedHostSuffixes");
        List<String> suffixes = new ArrayList<>();
        for (String part : (raw.isEmpty() ? DEFAULT_SUFFIXES : raw).split("[,;\\s]+")) {
            String suffix = part.strip().toLowerCase(Locale.ROOT).replaceAll("^\\.", "");
            if (!suffix.isEmpty()) {
                if (!HOST_SUFFIX.matcher(suffix).matches()) {
                    throw new IllegalStateException("allowedHostSuffixes contains an invalid domain");
                }
                suffixes.add(suffix);
            }
        }
        if (suffixes.isEmpty()) {
            throw new IllegalStateException("allowedHostSuffixes must name at least one domain");
        }
        return suffixes;
    }

    private static String text(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value == null ? "" : String.valueOf(value).strip();
    }
}
