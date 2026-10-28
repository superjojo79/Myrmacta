package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * TODO §109 — cérémonie hébergée vue du portail : le lien est demandé à l'ouverture, le clic
 * « signer » dans Myrmacta est refusé, le refus reste local (D-SIGNATURE-LINK).
 */
@AutoConfigureMockMvc
@Import(SignatureHostedCeremonyApiTest.HostedProvider.class)
class SignatureHostedCeremonyApiTest extends AbstractSignatureEngineTest {

    private static final String TOKEN = "X-Portal-Token";
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();
    private static final AtomicReference<String> LINK = new AtomicReference<>("https://yousign.app/signatures/abc");
    private static final AtomicBoolean FAIL_LINK = new AtomicBoolean();

    @TestConfiguration
    static class HostedProvider {
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
                    return new SignatureProviderCapabilities(true, true, true, true, Set.of("ELECTRONIC"));
                }

                @Override
                public SignatureSubmission submit(SubmitCommand command) {
                    return new SignatureSubmission("hosted:" + command.requestId(), false, "ELECTRONIC", Map.of());
                }

                @Override
                public SignatureProviderStatus status(String providerReference) {
                    return new SignatureProviderStatus(Optional.empty());
                }

                @Override
                public void cancel(String providerReference) {}

                @Override
                public SignedArtifacts fetchArtifacts(String providerReference) {
                    return new SignedArtifacts("SIGNED".getBytes(), "application/pdf", "PROOF".getBytes(),
                            "application/pdf", false);
                }

                @Override
                public Optional<String> signingUrl(String providerReference, int signerPosition) {
                    if (FAIL_LINK.get()) {
                        throw new IllegalStateException("https://evil.example/secret-token");
                    }
                    String base = LINK.get();
                    return base == null ? Optional.empty() : Optional.of(base + "/" + signerPosition);
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private String token;
    private UUID requestId;

    @BeforeEach
    void setUp() {
        LINK.set("https://yousign.app/signatures/abc");
        FAIL_LINK.set(false);
        String mail = "client-" + System.nanoTime() + "@example.org";
        UUID dossierId = dossier(Map.of(
                "signerA", mail,
                "signerB", "garant-" + System.nanoTime() + "@example.org")).id();
        startSignatureSmoke(dossierId, producedDoc(dossierId));
        token = sendAndCaptureToken(mail);
        requestId = signatureRepository.findByDossierId(dossierId).getFirst().id();
    }

    @Test
    void openingThePortalReturnsTheProviderLinkAndNotTheMyrmactaToken() throws Exception {
        String body = mockMvc.perform(get("/api/portal/signature").header(TOKEN, token).with(ip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hosted").value(true))
                .andExpect(jsonPath("$.simulated").value(false))
                .andExpect(jsonPath("$.signingUrl").value("https://yousign.app/signatures/abc/0"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(token);
    }

    @Test
    void aMissingLinkLeavesTheSignerWaiting() throws Exception {
        LINK.set(null);

        mockMvc.perform(get("/api/portal/signature").header(TOKEN, token).with(ip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hosted").value(true))
                .andExpect(jsonPath("$.signingUrl").value(nullValue()));
    }

    @Test
    void aProviderFailureDoesNotEchoTheLink() throws Exception {
        FAIL_LINK.set(true);

        String body = mockMvc.perform(get("/api/portal/signature").header(TOKEN, token).with(ip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hosted").value(true))
                .andExpect(jsonPath("$.signingUrl").value(nullValue()))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("secret-token");
    }

    @Test
    void signingInsideMyrmactaIsRefusedAndDecliningStaysLocal() throws Exception {
        mockMvc.perform(post("/api/portal/signature/sign").header(TOKEN, token).with(ip()))
                .andExpect(status().isConflict());
        assertThat(signatureRepository.findById(requestId).orElseThrow().status()).isEqualTo(SignatureStatus.PENDING);

        mockMvc.perform(post("/api/portal/signature/decline").header(TOKEN, token).with(ip())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"non\"}"))
                .andExpect(status().isOk());
        assertThat(signatureRepository.findById(requestId).orElseThrow().status()).isEqualTo(SignatureStatus.DECLINED);
    }

    private static RequestPostProcessor ip() {
        int n = IP_SEQUENCE.getAndIncrement();
        String addr = "10.44." + (n / 250 % 250) + "." + (1 + n % 250);
        return req -> {
            req.setRemoteAddr(addr);
            return req;
        };
    }
}
