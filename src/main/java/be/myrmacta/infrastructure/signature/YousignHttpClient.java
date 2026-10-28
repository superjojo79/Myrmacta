package be.myrmacta.infrastructure.signature;

import be.myrmacta.infrastructure.assistant.BoundedResponseBody;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Client HTTP du JDK vers l'API Yousign v3 (TODO §109, D-SIGNATURE-HTTP). Pas de redirection, corps
 * borné, secret et lien de signature absents des erreurs. L'hôte d'appel est fixé par
 * l'environnement ; l'hôte du lien de signature est contrôlé avant d'être rendu (D-SIGNATURE-LINK).
 */
final class YousignHttpClient implements YousignClient {

    static final URI SANDBOX = URI.create("https://api-sandbox.yousign.app/v3");
    static final URI PRODUCTION = URI.create("https://api.yousign.app/v3");

    /** Seuls les deux hôtes publics. Toute autre valeur, y compris une URL libre, est refusée. */
    static URI baseFor(String environment) {
        return switch (environment == null ? "" : environment) {
            case "sandbox" -> SANDBOX;
            case "production" -> PRODUCTION;
            default -> throw new YousignCallException("Yousign environment is not allowed");
        };
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_JSON_BYTES = 1024 * 1024;
    private static final int MAX_BINARY_BYTES = 20 * 1024 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http;
    private final URI base;
    private final String apiKey;

    YousignHttpClient(URI base, String apiKey) {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), base, apiKey);
    }

    YousignHttpClient(HttpClient http, URI base, String apiKey) {
        this.http = http;
        this.base = base;
        this.apiKey = apiKey;
    }

    @Override
    public UUID createSignatureRequest(String name, boolean orderedSigners, String externalId, String expirationDate) {
        ObjectNode body = JSON.createObjectNode();
        body.put("name", name == null || name.isBlank() ? "document" : clip(name, 128));
        body.put("delivery_mode", "none");
        body.put("ordered_signers", orderedSigners);
        if (externalId != null && !externalId.isBlank()) {
            body.put("external_id", clip(externalId, 255));
        }
        if (expirationDate != null && !expirationDate.isBlank()) {
            body.put("expiration_date", expirationDate);
        }
        JsonNode created = postJson("/signature_requests", body.toString());
        return uuid(created, "id");
    }

    @Override
    public Upload uploadSignablePdf(UUID requestId, String filename, byte[] pdf) {
        String boundary = "MyrmactaYousign" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipart(boundary, filename, pdf == null ? new byte[0] : pdf);
        HttpRequest request = authorized(uri("/signature_requests/" + requestId + "/documents"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        JsonNode uploaded = read(exchange(request, MAX_BINARY_BYTES));
        int anchors = uploaded.path("total_anchors").asInt(0);
        return new Upload(uuid(uploaded, "id"), anchors);
    }

    @Override
    public UUID addSigner(UUID requestId, String firstName, String lastName, String email) {
        ObjectNode info = JSON.createObjectNode();
        info.put("first_name", clip(firstName, 255));
        info.put("last_name", clip(lastName, 255));
        info.put("email", email);
        info.put("locale", "fr");
        ObjectNode body = JSON.createObjectNode();
        body.set("info", info);
        body.put("signature_level", "electronic_signature");
        body.put("signature_authentication_mode", "no_otp");
        JsonNode created = postJson("/signature_requests/" + requestId + "/signers", body.toString());
        return uuid(created, "id");
    }

    @Override
    public void activate(UUID requestId) {
        postJson("/signature_requests/" + requestId + "/activate", "{}");
    }

    @Override
    public Optional<String> signatureLink(UUID requestId, int signerPosition) {
        JsonNode signers = getJson("/signature_requests/" + requestId + "/signers");
        if (!signers.isArray() || signerPosition < 0 || signerPosition >= signers.size()) {
            throw new YousignCallException("Yousign signer position is unknown");
        }
        UUID signerId = uuid(signers.get(signerPosition), "id");
        JsonNode signer = getJson("/signature_requests/" + requestId + "/signers/" + signerId);
        JsonNode link = signer.get("signature_link");
        if (link == null || link.isNull() || link.asString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(allowedSigningLink(link.asString()));
    }

    @Override
    public void cancel(UUID requestId) {
        postJson("/signature_requests/" + requestId + "/cancel", "{\"reason\":\"other\"}");
    }

    @Override
    public byte[] downloadSignedPdf(UUID requestId) {
        JsonNode documents = getJson("/signature_requests/" + requestId + "/documents");
        if (!documents.isArray()) {
            throw new YousignCallException("Yousign returned an unexpected payload");
        }
        String documentId = null;
        for (JsonNode document : documents) {
            if ("signable_document".equals(document.path("nature").asString())) {
                documentId = document.path("id").asString();
                break;
            }
        }
        if (documentId == null || documentId.isBlank()) {
            throw new YousignCallException("Yousign returned an unexpected payload");
        }
        return getBytes("/signature_requests/" + requestId + "/documents/" + documentId + "/download?version=completed");
    }

    @Override
    public byte[] downloadAuditTrail(UUID requestId) {
        return getBytes("/signature_requests/" + requestId + "/audit_trails/download");
    }

    /** Lien https dont l'hôte est yousign.app ou yousign.com. Le lien lui-même n'est pas recopié dans l'erreur. */
    static String allowedSigningLink(String link) {
        URI uri;
        try {
            uri = URI.create(link);
        } catch (IllegalArgumentException ex) {
            throw new YousignCallException("Yousign signing URL is not allowed");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        boolean allowedHost = host.equals("yousign.app") || host.endsWith(".yousign.app")
                || host.equals("yousign.com") || host.endsWith(".yousign.com");
        int port = uri.getPort();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || !allowedHost
                || (port != -1 && port != 443)) {
            throw new YousignCallException("Yousign signing URL is not allowed");
        }
        return link;
    }

    private JsonNode postJson(String path, String json) {
        HttpRequest request = authorized(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return read(exchange(request, MAX_JSON_BYTES));
    }

    private JsonNode getJson(String path) {
        HttpRequest request = authorized(uri(path)).GET().build();
        return read(exchange(request, MAX_JSON_BYTES));
    }

    private byte[] getBytes(String path) {
        HttpRequest request = authorized(uri(path)).GET().build();
        HttpResponse<byte[]> response = exchange(request, MAX_BINARY_BYTES);
        if (response.statusCode() / 100 != 2) {
            throw new YousignCallException("Yousign answered HTTP " + response.statusCode());
        }
        return response.body();
    }

    private HttpRequest.Builder authorized(URI uri) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new YousignCallException("Yousign API key is missing");
        }
        return HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("Authorization", "Bearer " + apiKey);
    }

    private HttpResponse<byte[]> exchange(HttpRequest request, int maxBytes) {
        try {
            HttpResponse<byte[]> response = BoundedResponseBody.send(http, request, TIMEOUT, maxBytes);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                throw new YousignCallException("Yousign redirected the call");
            }
            return response;
        } catch (YousignCallException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new YousignCallException("Yousign call failed");
        }
    }

    private static JsonNode read(HttpResponse<byte[]> response) {
        if (response.statusCode() / 100 != 2) {
            throw new YousignCallException("Yousign answered HTTP " + response.statusCode());
        }
        try {
            return JSON.readTree(response.body());
        } catch (JacksonException ex) {
            throw new YousignCallException("Yousign returned an unexpected payload");
        }
    }

    private URI uri(String pathAndQuery) {
        String prefix = base.toString().endsWith("/") ? base.toString().substring(0, base.toString().length() - 1) : base.toString();
        return URI.create(prefix + pathAndQuery);
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(node.path(field).asString());
        } catch (IllegalArgumentException ex) {
            throw new YousignCallException("Yousign returned an unexpected payload");
        }
    }

    private static String clip(String value, int max) {
        String cleaned = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private static byte[] multipart(String boundary, String filename, byte[] pdf) {
        String safeName = filename == null || filename.isBlank() ? "document.pdf" : filename.replace("\"", "").replace("\\", "");
        String preamble = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"nature\"\r\n\r\n"
                + "signable_document\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"parse_anchors\"\r\n\r\n"
                + "true\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(preamble.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(pdf);
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
