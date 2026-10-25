package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * TODO §72, M6 — cycle complet sur le moteur réel : émission, invitations, décisions par l'API
 * publique, artefacts marqués, reprise du process et routage sur {@code signatureOutcome}
 * (S-DEC-1/2/3, S-OUT-1/3, S-DOC-1, S-STUB-1/3, S-PW-4/5 côté backend).
 */
@AutoConfigureMockMvc
class SignatureDecisionFlowEngineTest extends AbstractSignatureEngineTest {

    // Compteur et non System.nanoTime() : sous Windows nanoTime avance par pas de 100 ns, d'où
    // une poignée d'IP distinctes seulement — une IP « fraîche » retombait sur celle saturée par
    // le test de rate limit (429).
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    private static final String TOKEN = "X-Portal-Token";

    @Autowired
    private MockMvc mockMvc;

    private UUID dossierId;
    private String processInstanceId;
    private String tokenClient;
    private String tokenGarant;
    private SignatureRequest request;

    @BeforeEach
    void setUp() {
        String client = "client-" + System.nanoTime() + "@example.org";
        String garant = "garant-" + System.nanoTime() + "@example.org";
        dossierId = dossier(Map.of("signerA", client, "signerB", garant)).id();
        processInstanceId = startSignatureSmoke(dossierId, producedDoc(dossierId));
        tokenClient = sendAndCaptureToken(client);
        tokenGarant = sendAndCaptureToken(garant);
        request = signatureRepository.findByDossierId(dossierId).getFirst();
    }

    private static RequestPostProcessor ip() {
        int n = IP_SEQUENCE.getAndIncrement();
        String addr = "10.40." + (n / 250 % 250) + "." + (1 + n % 250);
        return req -> {
            req.setRemoteAddr(addr);
            return req;
        };
    }

    private String taskName() {
        return taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult().getName();
    }

    private List<DossierElement> signatureElements() {
        return elementRepository.findByDossierIdOrderByAddedAtAsc(dossierId).stream()
                .filter(e -> e.metadata().containsKey("signatureRole"))
                .toList();
    }

    @Test
    void requiredSignatureClosesSignedResumesTheProcessAndAttachesMarkedSimulatedDocuments() throws Exception {
        mockMvc.perform(post("/api/portal/signature/sign").header(TOKEN, tokenClient).with(ip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.requestStatus").doesNotExist());

        SignatureRequest stored = signatureRepository.findById(request.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(stored.outcomeDispatchedAt()).isNotNull();
        assertThat(signatureRepository.findSigners(request.id())).extracting(Signer::status)
                .containsExactly(SignerStatus.SIGNED, SignerStatus.REVOKED);
        // le process a repris et routé sur SIGNED
        assertThat(runtimeService.getVariable(processInstanceId, "signatureOutcome")).isEqualTo("SIGNED");
        assertThat(taskName()).isEqualTo("Signature obtenue");
        // S-DOC-1 : PRODUCED_DOC marqués, document signé filigrané, preuve PDF
        List<DossierElement> marked = signatureElements();
        assertThat(marked).hasSize(2).extracting(DossierElement::type).containsOnly(ElementType.PRODUCED_DOC);
        assertThat(marked).extracting(e -> e.metadata().get("signatureRole"))
                .containsExactly("SIGNED_DOCUMENT", "SIGNATURE_PROOF");
        assertThat(marked).allSatisfy(e -> {
            assertThat(e.metadata()).containsEntry("simulated", true)
                    .containsEntry("signatureRequestId", request.id().toString());
            assertThat(e.label()).contains("SIMULATION");
        });
        assertThat(TestPdfs.text(storage.retrieve(TENANT, marked.get(0).storageRef())))
                .contains("Contrat de test", "SIMULATION");
        assertThat(TestPdfs.text(storage.retrieve(TENANT, marked.get(1).storageRef())))
                .contains("Client", "SIGNED", request.id().toString());
    }

    @Test
    void secondSubmissionAndTokensOfARevokedSignerAreRefused() throws Exception {
        mockMvc.perform(post("/api/portal/signature/sign").header(TOKEN, tokenClient).with(ip()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/portal/signature/sign").header(TOKEN, tokenClient).with(ip()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/portal/signature/sign").header(TOKEN, tokenGarant).with(ip()))
                .andExpect(status().isNotFound());
        assertThat(signatureElements()).hasSize(2);
    }

    @Test
    void requiredDeclineClosesDeclinedWithoutArtifactsAndRoutesTheProcess() throws Exception {
        mockMvc.perform(post("/api/portal/signature/decline")
                        .header(TOKEN, tokenClient)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Je ne suis pas d'accord\"}")
                        .with(ip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        assertThat(signatureRepository.findById(request.id()).orElseThrow().status())
                .isEqualTo(SignatureStatus.DECLINED);
        assertThat(signatureRepository.findSigners(request.id()).getFirst().declineReason())
                .isEqualTo("Je ne suis pas d'accord");
        assertThat(signatureElements()).isEmpty();
        assertThat(runtimeService.getVariable(processInstanceId, "signatureOutcome")).isEqualTo("DECLINED");
        assertThat(taskName()).isEqualTo("Signature non obtenue");
    }

    @Test
    void optionalDeclineDoesNotStopTheRequestAndTheRequiredSignatureStillSucceeds() throws Exception {
        mockMvc.perform(post("/api/portal/signature/decline").header(TOKEN, tokenGarant).with(ip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
        assertThat(signatureRepository.findById(request.id()).orElseThrow().status())
                .isEqualTo(SignatureStatus.PENDING);
        assertThat(taskService.createTaskQuery().processInstanceId(processInstanceId).count()).isZero();

        mockMvc.perform(post("/api/portal/signature/sign").header(TOKEN, tokenClient).with(ip()))
                .andExpect(status().isOk());

        assertThat(signatureRepository.findById(request.id()).orElseThrow().status())
                .isEqualTo(SignatureStatus.SIGNED);
        assertThat(taskName()).isEqualTo("Signature obtenue");
    }

    @Test
    void tooLongDeclineReasonIsABadRequestAndChangesNothing() throws Exception {
        mockMvc.perform(post("/api/portal/signature/decline")
                        .header(TOKEN, tokenClient)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "x".repeat(600) + "\"}")
                        .with(ip()))
                .andExpect(status().isBadRequest());
        assertThat(signatureRepository.findSigners(request.id())).extracting(Signer::status)
                .containsOnly(SignerStatus.PENDING);
    }
}
