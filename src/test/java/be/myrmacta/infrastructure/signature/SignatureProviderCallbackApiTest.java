package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.port.in.DispatchSignatureOutcomesUseCase;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §72, M8 — retour d'un fournisseur « hébergé » (adaptateur factice) par la route générique
 * {@code /api/callbacks/{instanceId}} : HMAC horodaté, idempotence, rejeu, événement tardif
 * (S-OUT-4, S-OUT-5, S-CONN-1) sur le moteur réel.
 */
@AutoConfigureMockMvc
@Import(SignatureProviderCallbackApiTest.HostedProviderConfig.class)
class SignatureProviderCallbackApiTest extends AbstractSignatureEngineTest {

    private static final String SECRET = "provider-secret-provider-secret-1234567890";

    /** Fournisseur factice hébergé : référence propre, callback JSON, artefacts non simulés. */
    @TestConfiguration
    static class HostedProviderConfig {
        @Bean
        @Primary
        SignaturePort hostedSignaturePort() {
            return new SignaturePort() {
                @Override
                public String code() {
                    return "hosted";
                }

                @Override
                public SignatureProviderCapabilities capabilities() {
                    return new SignatureProviderCapabilities(true, true, true, true, Set.of("ADVANCED"));
                }

                @Override
                public SignatureSubmission submit(SubmitCommand command) {
                    return new SignatureSubmission("hosted:" + command.requestId(), false, "ADVANCED", Map.of());
                }

                @Override
                public SignatureProviderStatus status(String providerReference) {
                    return new SignatureProviderStatus(java.util.Optional.empty());
                }

                @Override
                public void cancel(String providerReference) {}

                @Override
                public SignedArtifacts fetchArtifacts(String providerReference) {
                    return new SignedArtifacts("SIGNED".getBytes(), "application/pdf", "PROOF".getBytes(),
                            "application/pdf", false);
                }

                @Override
                @SuppressWarnings("unchecked")
                public SignatureCallbackEvent parseCallback(Object payload) {
                    if (!(payload instanceof Map<?, ?> map) || map.get("reference") == null || map.get("outcome") == null) {
                        throw new IllegalArgumentException("not a hosted-provider event");
                    }
                    Map<UUID, SignerStatus> decisions = new java.util.HashMap<>();
                    if (map.get("signers") instanceof Map<?, ?> signers) {
                        signers.forEach((k, v) -> decisions.put(UUID.fromString(k.toString()),
                                SignerStatus.valueOf(v.toString())));
                    }
                    return new SignatureCallbackEvent(map.get("reference").toString(),
                            SignatureOutcome.valueOf(map.get("outcome").toString()), decisions);
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private ConnectorInstanceRepository connectorInstances;

    @Autowired
    private DispatchSignatureOutcomesUseCase dispatcher;

    private UUID connectorId;
    private UUID dossierId;
    private String processInstanceId;
    private SignatureRequest request;

    @BeforeEach
    void setUp() {
        connectorId = connectorInstances.save(new ConnectorInstanceData(UUID.randomUUID(), TENANT, "signature-hosted-" + System.nanoTime(),
                        "signature", true, Map.of("webhookAuthType", "HMAC_SHA256", "webhookSecret", SECRET)))
                .id();
        dossierId = dossier(Map.of(
                        "signerA", "client-" + System.nanoTime() + "@example.org",
                        "signerB", "garant-" + System.nanoTime() + "@example.org"))
                .id();
        processInstanceId = startSignatureSmoke(dossierId, producedDoc(dossierId));
        request = signatureRepository.findByDossierId(dossierId).getFirst();
    }

    private MockHttpServletRequestBuilder callback(Map<String, Object> body, String idempotencyKey, String secret)
            throws Exception {
        byte[] raw = json.writeValueAsBytes(body);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + "." + idempotencyKey + ".").getBytes(StandardCharsets.UTF_8));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(raw));
        return post("/api/callbacks/{id}", connectorId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Webhook-Timestamp", timestamp)
                .header("X-Signature-256", signature)
                .content(raw);
    }

    private Map<String, Object> event(SignatureOutcome outcome, Map<String, String> signers) {
        return Map.of("reference", request.providerReference(), "outcome", outcome.name(), "signers", signers);
    }

    private List<DossierElement> marked() {
        return elementRepository.findByDossierIdOrderByAddedAtAsc(dossierId).stream()
                .filter(e -> e.metadata().containsKey("signatureRole"))
                .toList();
    }

    @Test
    void authenticatedSignedEventClosesTheRequestAttachesRealArtifactsAndResumesTheProcess() throws Exception {
        assertThat(request.providerCode()).isEqualTo("hosted");
        assertThat(request.simulated()).isFalse();
        Signer client = signatureRepository.findSigners(request.id()).getFirst();

        mockMvc.perform(callback(event(SignatureOutcome.SIGNED, Map.of(client.id().toString(), "SIGNED")),
                        "evt-1", SECRET))
                .andExpect(status().isAccepted());

        SignatureRequest stored = signatureRepository.findById(request.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(signatureRepository.findSigners(request.id())).extracting(Signer::status)
                .containsExactly(SignerStatus.SIGNED, SignerStatus.REVOKED);
        assertThat(marked()).isEmpty();
        assertThat(runtimeService.getVariable(processInstanceId, "signatureOutcome")).isNull();

        assertThat(dispatcher.dispatch(request.id())).isTrue();

        assertThat(marked()).hasSize(2).allSatisfy(e -> assertThat(e.metadata()).containsEntry("simulated", false));
        assertThat(runtimeService.getVariable(processInstanceId, "signatureOutcome")).isEqualTo("SIGNED");
        assertThat(taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult().getName())
                .isEqualTo("Signature obtenue");
    }

    @Test
    void replayedAndLateEventsAreAcknowledgedWithoutEffect() throws Exception {
        // S-OUT-5
        mockMvc.perform(callback(event(SignatureOutcome.SIGNED, Map.of()), "evt-1", SECRET))
                .andExpect(status().isAccepted());
        mockMvc.perform(callback(event(SignatureOutcome.SIGNED, Map.of()), "evt-1", SECRET))
                .andExpect(status().isAccepted()); // même clé d'idempotence : statut mis en cache
        mockMvc.perform(callback(event(SignatureOutcome.DECLINED, Map.of()), "evt-2", SECRET))
                .andExpect(status().isAccepted()); // clé nouvelle, demande déjà close : ignoré

        assertThat(signatureRepository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(marked()).isEmpty();
        dispatcher.dispatch(request.id());
        assertThat(marked()).hasSize(2);
        dispatcher.dispatch(request.id());
        assertThat(marked()).hasSize(2);
    }

    @Test
    void wrongSignatureAndMissingHeadersAreRejectedWithoutAnyEffect() throws Exception {
        // S-OUT-4
        mockMvc.perform(callback(event(SignatureOutcome.SIGNED, Map.of()), "evt-1", "another-secret-another-secret-123456"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/callbacks/{id}", connectorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());

        assertThat(signatureRepository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.PENDING);
    }

    @Test
    void malformedEventIsABadRequestAndAnUnknownReferenceIsRetriedWithTheSameKey() throws Exception {
        // revue adversariale n°8 : 503 (pas de 400 mémorisé) et la clé d'idempotence est libérée
        Map<String, Object> unknown = Map.of("reference", "hosted:" + UUID.randomUUID(), "outcome", "SIGNED");
        mockMvc.perform(callback(unknown, "evt-3", SECRET)).andExpect(status().isServiceUnavailable());
        mockMvc.perform(callback(unknown, "evt-3", SECRET)).andExpect(status().isServiceUnavailable());
        mockMvc.perform(callback(Map.of("hello", "world"), "evt-4", SECRET)).andExpect(status().isBadRequest());
        assertThat(signatureRepository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.PENDING);
    }
}
