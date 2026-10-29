package be.myrmacta.infrastructure.signature;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.infrastructure.connector.builtin.SignatureCallbackConnector;
import be.myrmacta.infrastructure.signature.DocusignHttpClient.DocusignTokenCache;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adaptateur DocuSign (TODO §109, D-SIGNATURE-PROVIDER). L'URL de signature n'est pas conservée :
 * elle est créée à l'ouverture et expire en quelques minutes (D-SIGNATURE-LINK). L'URL de retour
 * ne contient pas le jeton Myrmacta (D-SIGNATURE-RETURN). Une ancre absente fait échouer la
 * demande (D-SIGNATURE-ANCHOR).
 */
public final class DocusignSignatureAdapter implements SignaturePort {

    public static final String CODE = "docusign";
    private static final String LEVEL = "ELECTRONIC";

    private final String tenantId;
    private final ConnectorInstanceRepository instances;
    private final String publicBaseUrl;
    private final Clock clock;
    private final DocusignTokenCache cache;
    private final DocusignClient fixed;

    public DocusignSignatureAdapter(String tenantId, ConnectorInstanceRepository instances, String publicBaseUrl) {
        this(tenantId, instances, publicBaseUrl, Clock.systemUTC(), null);
    }

    DocusignSignatureAdapter(
            String tenantId, ConnectorInstanceRepository instances, String publicBaseUrl, Clock clock, DocusignClient fixed) {
        this.tenantId = tenantId;
        this.instances = instances;
        this.publicBaseUrl = publicBaseUrl;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.cache = new DocusignTokenCache();
        this.fixed = fixed;
    }

    DocusignSignatureAdapter(DocusignClient client, String publicBaseUrl) {
        this(null, null, publicBaseUrl, Clock.systemUTC(), client);
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public SignatureProviderCapabilities capabilities() {
        // D-SIGNATURE-OPTIONAL : pas de signataire facultatif tant que l'API publique ne le distingue pas.
        return new SignatureProviderCapabilities(true, false, true, true, Set.of(LEVEL));
    }

    @Override
    public SignatureSubmission submit(SubmitCommand command) {
        if (command.signers().isEmpty()) {
            throw new IllegalStateException("DocuSign needs at least one signer");
        }
        List<DocusignClient.SignerSpec> signers = new ArrayList<>();
        for (SubmitSigner signer : command.signers()) {
            if (signer.email() == null || signer.email().isBlank()) {
                throw new IllegalStateException("DocuSign needs an email for every signer");
            }
            signers.add(new DocusignClient.SignerSpec(displayName(signer.displayName()), signer.email(), signer.position()));
        }
        UUID envelopeId = client().createEnvelope(
                command.documentName(),
                command.mode() == SignatureMode.SEQUENTIAL,
                command.requestId().toString(),
                command.deadlineAt(),
                signers,
                command.document());
        return new SignatureSubmission(DocusignCallbackParser.REFERENCE_PREFIX + envelopeId, false, LEVEL, Map.of());
    }

    @Override
    public SignatureProviderStatus status(String providerReference) {
        return new SignatureProviderStatus(Optional.empty());
    }

    @Override
    public SignatureCallbackEvent parseCallback(Object payload) {
        return DocusignCallbackParser.parse(payload)
                .orElseThrow(() -> new IllegalArgumentException("DocuSign event does not close the request"));
    }

    @Override
    public void cancel(String providerReference) {
        envelopeId(providerReference).ifPresent(id -> client().voidEnvelope(id));
    }

    @Override
    public SignedArtifacts fetchArtifacts(String providerReference) {
        UUID id = envelopeId(providerReference)
                .orElseThrow(() -> new IllegalArgumentException("not a DocuSign reference"));
        DocusignClient current = client();
        return new SignedArtifacts(current.downloadSignedPdf(id), "application/pdf",
                current.downloadCertificate(id), "application/pdf", false);
    }

    @Override
    public Optional<String> signingUrl(String providerReference, int signerPosition) {
        UUID id = envelopeId(providerReference)
                .orElseThrow(() -> new IllegalArgumentException("not a DocuSign reference"));
        return client().recipientView(id, signerPosition, returnUrl(publicBaseUrl));
    }

    /**
     * Page neutre du portail, sans fragment ni requête : le jeton Myrmacta vit dans le fragment
     * du lien d'invitation et ne doit pas partir chez DocuSign (D-SIGNATURE-RETURN).
     */
    static String returnUrl(String publicBaseUrl) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new DocusignCallException("DocuSign return URL is not configured");
        }
        String base = publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + "/portal/";
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new DocusignCallException("DocuSign return URL is not allowed");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host);
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean httpLoop = loopback && "http".equalsIgnoreCase(uri.getScheme());
        if ((!https && !httpLoop) || uri.getUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || uri.getHost() == null) {
            throw new DocusignCallException("DocuSign return URL is not allowed");
        }
        return url;
    }

    private DocusignClient client() {
        if (fixed != null) {
            return fixed;
        }
        List<ConnectorInstanceData> found = instances.listByTenantId(tenantId).stream()
                .filter(instance -> instance.enabled() && SignatureCallbackConnector.TYPE.equals(instance.connectorType()))
                .toList();
        if (found.size() != 1) {
            throw new DocusignCallException("exactly one enabled signature connector is required");
        }
        Map<String, Object> config = found.get(0).config();
        String integrationKey = text(config.get("docusignIntegrationKey"));
        String user = text(config.get("docusignUserId"));
        String privateKey = text(config.get("docusignPrivateKey"));
        if (integrationKey.isBlank() || user.isBlank() || privateKey.isBlank()) {
            throw new DocusignCallException("DocuSign credentials are missing");
        }
        Object environment = config.get("docusignEnvironment");
        String name = environment == null || String.valueOf(environment).isBlank()
                ? "sandbox"
                : String.valueOf(environment);
        return new DocusignHttpClient(name, integrationKey, user, privateKey, text(config.get("docusignAccountId")),
                clock, cache);
    }

    private static Optional<UUID> envelopeId(String providerReference) {
        if (providerReference == null || !providerReference.startsWith(DocusignCallbackParser.REFERENCE_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(
                    providerReference.substring(DocusignCallbackParser.REFERENCE_PREFIX.length())));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String displayName(String displayName) {
        String cleaned = displayName == null ? "" : displayName.trim().replaceAll("\\s+", " ");
        return cleaned.isBlank() ? "Signataire" : cleaned;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
