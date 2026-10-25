package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Revue adversariale de la PR signature, n°5 et n°6 — sur le moteur réel : une attente interrompue par une
 * frontière annule la demande ; deux blocs en branches parallèles se corrèlent chacun à leur propre demande.
 */
@AutoConfigureMockMvc
class SignatureBpmnEdgeCasesEngineTest extends AbstractSignatureEngineTest {

    // Compteur et non System.nanoTime() : sous Windows nanoTime avance par pas de 100 ns, d'où
    // une poignée d'IP distinctes seulement — une IP « fraîche » retombait sur celle saturée par
    // le test de rate limit (429).
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    private static final String TOKEN = "X-Portal-Token";

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor ip() {
        int n = IP_SEQUENCE.getAndIncrement();
        String addr = "10.50." + (n / 250 % 250) + "." + (1 + n % 250);
        return req -> {
            req.setRemoteAddr(addr);
            return req;
        };
    }

    private String start(String key, UUID dossierId) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("dossierId", dossierId.toString());
        variables.put("generatedDocumentId", producedDoc(dossierId));
        return runtimeService.startProcessInstanceByKey(key, variables).getId();
    }

    private String taskNames(String processInstanceId) {
        return taskService.createTaskQuery().processInstanceId(processInstanceId).list().stream()
                .map(t -> t.getName()).sorted().toList().toString();
    }

    @Test
    void interruptedWaitCancelsTheOpenRequestAndKillsItsLink() throws Exception {
        String a = "client-" + System.nanoTime() + "@example.org";
        UUID dossierId = dossier(Map.of("signerA", a, "signerB", "x-" + System.nanoTime() + "@example.org")).id();
        String processInstanceId = start("signatureInterruptSmoke", dossierId);
        String token = sendAndCaptureToken(a);
        SignatureRequest request = signatureRepository.findByDossierId(dossierId).getFirst();
        assertThat(request.status()).isEqualTo(SignatureStatus.PENDING);

        runtimeService.signalEventReceived("abortSignature");

        assertThat(taskNames(processInstanceId)).isEqualTo("[Abandonné]");
        SignatureRequest after = signatureRepository.findById(request.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(SignatureStatus.CANCELLED);
        assertThat(after.statusReason()).isEqualTo("WAIT_INTERRUPTED");
        assertThat(after.outcomeDispatchedAt()).isNotNull(); // rien à reprendre
        mockMvc.perform(get("/api/portal/signature").header(TOKEN, token).with(ip()))
                .andExpect(status().isNotFound());
    }

    @Test
    void twoSignatureBlocksInParallelBranchesEachResumeTheirOwnBranch() throws Exception {
        String a = "client-" + System.nanoTime() + "@example.org";
        String b = "garant-" + System.nanoTime() + "@example.org";
        UUID dossierId = dossier(Map.of("signerA", a, "signerB", b)).id();
        String processInstanceId = start("signatureParallelBlocksSmoke", dossierId);
        String tokenA = sendAndCaptureToken(a);
        String tokenB = sendAndCaptureToken(b);
        List<SignatureRequest> requests = signatureRepository.findByDossierId(dossierId);
        assertThat(requests).hasSize(2).allMatch(r -> r.status() == SignatureStatus.PENDING);

        mockMvc.perform(post("/api/portal/signature/sign").header(TOKEN, tokenB).with(ip()))
                .andExpect(status().isOk());
        // une seule branche a repris : le join attend encore l'autre
        assertThat(taskNames(processInstanceId)).isEqualTo("[]");
        assertThat(signatureRepository.findByDossierId(dossierId))
                .extracting(SignatureRequest::status)
                .containsExactlyInAnyOrder(SignatureStatus.SIGNED, SignatureStatus.PENDING);

        mockMvc.perform(post("/api/portal/signature/sign").header(TOKEN, tokenA).with(ip()))
                .andExpect(status().isOk());
        assertThat(taskNames(processInstanceId)).isEqualTo("[Toutes signées]");
    }
}
