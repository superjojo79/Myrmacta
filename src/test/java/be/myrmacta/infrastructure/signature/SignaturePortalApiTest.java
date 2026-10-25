package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.infrastructure.web.portal.FixedClockPortalRateLimiterConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** TODO §72, M5 — API publique de signature (S-ACCESS-1/2/3/4/5, S-DEC-5 côté API). */
@AutoConfigureMockMvc
@Import(FixedClockPortalRateLimiterConfig.class)
class SignaturePortalApiTest extends AbstractSignatureEngineTest {

    // Compteur et non System.nanoTime() : sous Windows nanoTime avance par pas de 100 ns, d'où
    // une poignée d'IP distinctes seulement — une IP « fraîche » retombait sur celle saturée par
    // le test de rate limit (429).
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    private static final String TOKEN = "X-Portal-Token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    private UUID dossierId;
    private SignatureRequest request;
    private String tokenA;
    private String tokenB;
    private String mailA;

    @BeforeEach
    void setUp() {
        mailA = "client-" + System.nanoTime() + "@example.org";
        String mailB = "garant-" + System.nanoTime() + "@example.org";
        dossierId = dossier(Map.of("signerA", mailA, "signerB", mailB)).id();
        startSignatureSmoke(dossierId, producedDoc(dossierId));
        tokenA = sendAndCaptureToken(mailA);
        tokenB = sendAndCaptureToken(mailB);
        request = signatureRepository.findByDossierId(dossierId).getFirst();
    }

    private static RequestPostProcessor ip(String addr) {
        return req -> {
            req.setRemoteAddr(addr);
            return req;
        };
    }

    private static String freshIp() {
        int n = IP_SEQUENCE.getAndIncrement();
        return "10.30." + (n / 250 % 250) + "." + (1 + n % 250);
    }

    private Signer signer(String name) {
        return signatureRepository.findSigners(request.id()).stream()
                .filter(s -> s.displayName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void validTokenReturnsExactlyTheMinimalContext() throws Exception {
        // S-ACCESS-1, S-ACCESS-4
        String body = mockMvc.perform(get("/api/portal/signature").header(TOKEN, tokenA).with(ip(freshIp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signerName").value("Client"))
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.documentLabel").value("Contrat"))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(new ArrayList<>(json.propertyNames()))
                .containsExactlyInAnyOrder("dossierReference", "documentLabel", "signerName", "required",
                        "deadlineAt", "simulated");
        assertThat(body).doesNotContain("@example.org").doesNotContain(request.id().toString());
    }

    @Test
    void documentIsStreamedWithHardenedHeaders() throws Exception {
        mockMvc.perform(get("/api/portal/signature/document").header(TOKEN, tokenA).with(ip(freshIp())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(content().bytes(storage.retrieve(TENANT, request.sourceStorageRef())));
    }

    @Test
    void everyDenialReturnsTheSameBody() throws Exception {
        // S-ACCESS-2
        String unknown = denied(get("/api/portal/signature").header(TOKEN, "x".repeat(43)));
        String missing = denied(get("/api/portal/signature"));
        String inQuery = denied(get("/api/portal/signature").param("token", tokenA));
        String overlong = denied(get("/api/portal/signature/document").header(TOKEN, "y".repeat(500)));

        // signataire révoqué, signé, demande annulée : mêmes réponses
        signatureRepository.updateSigner(signer("Garant").decided(SignerStatus.REVOKED, java.time.Instant.now(), null));
        String revoked = denied(get("/api/portal/signature").header(TOKEN, tokenB));
        signatureRepository.updateSigner(signer("Client").decided(SignerStatus.SIGNED, java.time.Instant.now(), null));
        String signed = denied(get("/api/portal/signature").header(TOKEN, tokenA));
        signatureRepository.updateRequest(
                signatureRepository.findById(request.id()).orElseThrow().closed(SignatureOutcome.CANCELLED, null,
                        java.time.Instant.now()));
        String closed = denied(get("/api/portal/signature/document").header(TOKEN, tokenA));

        assertThat(List.of(missing, inQuery, overlong, revoked, signed, closed)).allSatisfy(b -> assertThat(b).isEqualTo(unknown));
        assertThat(unknown).doesNotContain("SIGNED").doesNotContain("CANCELLED").doesNotContain("REVOKED");
    }

    private String denied(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder)
            throws Exception {
        return mockMvc.perform(builder.with(ip(freshIp())))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void aTokenNeverOpensAnotherRequestNorTakesIdentifiersFromTheClient() throws Exception {
        // S-ACCESS-3, S-ACCESS-4 : jeton du dossier 1 ; demande du dossier 2 hors de portée
        UUID otherDossier = dossier(Map.of(
                        "signerA", "autre-" + System.nanoTime() + "@example.org",
                        "signerB", "autre2-" + System.nanoTime() + "@example.org"))
                .id();
        String otherRef = producedDoc(otherDossier);
        startSignatureSmoke(otherDossier, otherRef);
        SignatureRequest other = signatureRepository.findByDossierId(otherDossier).getFirst();

        String body = mockMvc.perform(get("/api/portal/signature")
                        .param("requestId", other.id().toString())
                        .param("dossierId", otherDossier.toString())
                        .header(TOKEN, tokenA)
                        .with(ip(freshIp())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Client").doesNotContain(other.id().toString());
        mockMvc.perform(get("/api/portal/signature/document")
                        .param("requestId", other.id().toString())
                        .header(TOKEN, tokenA)
                        .with(ip(freshIp())))
                .andExpect(content().bytes(storage.retrieve(TENANT, request.sourceStorageRef())));
    }

    @Test
    void perIpRateLimitAppliesBeforeAnyLookup() throws Exception {
        // S-ACCESS-5
        String ip = freshIp();
        int refused = 0;
        for (int i = 0; i < 40; i++) {
            int code = mockMvc.perform(get("/api/portal/signature").header(TOKEN, "z".repeat(43)).with(ip(ip)))
                    .andReturn().getResponse().getStatus();
            if (code == 429) {
                refused++;
            }
        }
        assertThat(refused).isGreaterThanOrEqualTo(10);
        mockMvc.perform(get("/api/portal/signature").header(TOKEN, tokenA).with(ip(freshIp())))
                .andExpect(status().isOk());
    }
}
