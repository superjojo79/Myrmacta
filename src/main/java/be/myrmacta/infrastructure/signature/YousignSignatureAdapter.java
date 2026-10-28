package be.myrmacta.infrastructure.signature;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.infrastructure.connector.builtin.SignatureCallbackConnector;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adaptateur Yousign (TODO §109, D-SIGNATURE-PROVIDER). L'URL de signature n'est pas conservée à la
 * création (D-SIGNATURE-LINK). Un PDF sans ancre pour chaque signataire fait échouer la demande et
 * annule le brouillon (D-SIGNATURE-ANCHOR).
 */
public final class YousignSignatureAdapter implements SignaturePort {

    public static final String CODE = "yousign";
    private static final String LEVEL = "ELECTRONIC";

    private final String tenantId;
    private final ConnectorInstanceRepository instances;
    private final YousignClient fixed;

    public YousignSignatureAdapter(String tenantId, ConnectorInstanceRepository instances) {
        this.tenantId = tenantId;
        this.instances = instances;
        this.fixed = null;
    }

    YousignSignatureAdapter(YousignClient client) {
        this.tenantId = null;
        this.instances = null;
        this.fixed = client;
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
            throw new IllegalStateException("Yousign needs at least one signer");
        }
        for (SubmitSigner signer : command.signers()) {
            if (signer.email() == null || signer.email().isBlank()) {
                throw new IllegalStateException("Yousign needs an email for every signer");
            }
        }
        YousignClient client = client();
        boolean ordered = command.mode() == SignatureMode.SEQUENTIAL;
        String expiration = command.deadlineAt() == null
                ? null
                : command.deadlineAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
        UUID requestId = client.createSignatureRequest(
                command.documentName(), ordered, command.requestId().toString(), expiration);
        YousignClient.Upload upload = client.uploadSignablePdf(requestId, command.documentName(), command.document());
        if (upload.totalAnchors() < command.signers().size()) {
            cancelQuietly(client, requestId);
            throw new IllegalStateException("the document is missing a signature anchor");
        }
        for (SubmitSigner signer : command.signers()) {
            String[] name = nameParts(signer.displayName());
            client.addSigner(requestId, name[0], name[1], signer.email());
        }
        client.activate(requestId);
        return new SignatureSubmission(YousignCallbackParser.REFERENCE_PREFIX + requestId, false, LEVEL, Map.of());
    }

    @Override
    public SignatureProviderStatus status(String providerReference) {
        return new SignatureProviderStatus(Optional.empty());
    }

    @Override
    public SignatureCallbackEvent parseCallback(Object payload) {
        return YousignCallbackParser.parse(payload)
                .orElseThrow(() -> new IllegalArgumentException("Yousign event does not close the request"));
    }

    @Override
    public void cancel(String providerReference) {
        Optional<UUID> id = requestId(providerReference);
        if (id.isEmpty()) {
            return;
        }
        cancelQuietly(client(), id.get());
    }

    @Override
    public SignedArtifacts fetchArtifacts(String providerReference) {
        UUID id = requestId(providerReference)
                .orElseThrow(() -> new IllegalArgumentException("not a Yousign reference"));
        YousignClient client = client();
        return new SignedArtifacts(client.downloadSignedPdf(id), "application/pdf",
                client.downloadAuditTrail(id), "application/pdf", false);
    }

    @Override
    public Optional<String> signingUrl(String providerReference, int signerPosition) {
        UUID id = requestId(providerReference)
                .orElseThrow(() -> new IllegalArgumentException("not a Yousign reference"));
        return client().signatureLink(id, signerPosition);
    }

    private YousignClient client() {
        if (fixed != null) {
            return fixed;
        }
        List<ConnectorInstanceData> found = instances.listByTenantId(tenantId).stream()
                .filter(instance -> instance.enabled() && SignatureCallbackConnector.TYPE.equals(instance.connectorType()))
                .toList();
        if (found.size() != 1) {
            throw new YousignCallException("exactly one enabled signature connector is required");
        }
        Map<String, Object> config = found.get(0).config();
        Object key = config.get("yousignApiKey");
        if (!(key instanceof String text) || text.isBlank()) {
            throw new YousignCallException("Yousign API key is missing");
        }
        Object environment = config.get("yousignEnvironment");
        String name = environment == null || String.valueOf(environment).isBlank() ? "sandbox" : String.valueOf(environment);
        return new YousignHttpClient(YousignHttpClient.baseFor(name), text);
    }

    private static void cancelQuietly(YousignClient client, UUID requestId) {
        try {
            client.cancel(requestId);
        } catch (RuntimeException ex) {
            // D-SIGNATURE-ANCHOR : l'annulation du brouillon est au mieux.
        }
    }

    private static Optional<UUID> requestId(String providerReference) {
        if (providerReference == null || !providerReference.startsWith(YousignCallbackParser.REFERENCE_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(providerReference.substring(YousignCallbackParser.REFERENCE_PREFIX.length())));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    static String[] nameParts(String displayName) {
        String cleaned = displayName == null ? "" : displayName.trim().replaceAll("\\s+", " ");
        if (cleaned.isBlank()) {
            return new String[] {"Signataire", "-"};
        }
        int space = cleaned.lastIndexOf(' ');
        if (space < 0) {
            return new String[] {cleaned, "-"};
        }
        return new String[] {cleaned.substring(0, space), cleaned.substring(space + 1)};
    }
}
