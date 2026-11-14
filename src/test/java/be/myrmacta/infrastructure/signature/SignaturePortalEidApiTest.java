package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import be.myrmacta.infrastructure.web.portal.FixedClockPortalRateLimiterConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * TODO §138 — le PDF signé téléversé par le portail (chaîne anonyme, jeton en en-tête) : succès, document de
 * travail courant, motifs de refus (422 avec {@code code}), taille (413), refus d'accès uniforme (404), clic
 * « Signer » refusé (409), limite par IP avant lecture du corps.
 */
@AutoConfigureMockMvc
@Import(FixedClockPortalRateLimiterConfig.class)
class SignaturePortalEidApiTest extends AbstractEidSignatureEngineTest {

    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();
    private static final String TOKEN = "X-Portal-Token";
    private static final String UPLOAD = "/api/portal/signature/signed-document";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignedUploadRepository uploads;

    private SignatureRequest request;
    private String tokenA;
    private String tokenB;
    private byte[] source;

    @BeforeEach
    void setUp() {
        String mailA = "client-" + System.nanoTime() + "@example.org";
        String mailB = "garant-" + System.nanoTime() + "@example.org";
        UUID dossierId = dossier(Map.of("signerA", mailA, "signerB", mailB)).id();
        startSignatureSmoke(dossierId, producedDoc(dossierId));
        tokenA = sendAndCaptureToken(mailA);
        tokenB = sendAndCaptureToken(mailB);
        request = signatureRepository.findByDossierId(dossierId).getFirst();
        source = storage.retrieve(TENANT, request.sourceStorageRef());
    }

    private static RequestPostProcessor ip() {
        int n = IP_SEQUENCE.getAndIncrement();
        String addr = "10.50." + (n / 250 % 250) + "." + (1 + n % 250);
        return req -> {
            req.setRemoteAddr(addr);
            return req;
        };
    }

    private MockMultipartHttpServletRequestBuilder upload(String token, byte[] pdf) {
        MockMultipartHttpServletRequestBuilder builder =
                multipart(UPLOAD).file(new MockMultipartFile("file", "signe.pdf", "application/pdf", pdf));
        if (token != null) {
            builder.header(TOKEN, token);
        }
        return builder;
    }

    private Signer signer(String name) {
        return signatureRepository.findSigners(request.id()).stream()
                .filter(s -> s.displayName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void contextAnnouncesTheUploadAndTheDocumentIsTheSourceUntilSomeoneSigns() throws Exception {
        mockMvc.perform(get("/api/portal/signature").header(TOKEN, tokenA).with(ip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedUpload").value(true))
                .andExpect(jsonPath("$.hosted").value(false))
                .andExpect(jsonPath("$.simulated").value(false));
        mockMvc.perform(get("/api/portal/signature/document").header(TOKEN, tokenA).with(ip()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(source));
    }

    @Test
    void theSignedDocumentBecomesTheDocumentHandedToTheNextSigner() throws Exception {
        // D-EID-CHAIN : le garant (facultatif) signe en premier ; le client reçoit son PDF signé
        byte[] bySecond = FakeSignedPdfVerifier.signed(source, "Garant Dupont");
        mockMvc.perform(upload(tokenB, bySecond).with(ip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"));
        assertThat(signatureRepository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.PENDING);

        mockMvc.perform(get("/api/portal/signature/document").header(TOKEN, tokenA).with(ip()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bySecond));

        byte[] byFirst = FakeSignedPdfVerifier.signed(bySecond, "Client Martin");
        mockMvc.perform(upload(tokenA, byFirst).with(ip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"));

        assertThat(signatureRepository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(uploads.findByRequestId(request.id())).hasSize(2);
        assertThat(uploads.findByRequestId(request.id()).get(1).evidence().signerName()).isEqualTo("Client Martin");
        assertThat(signatureRepository.findSigners(request.id())).extracting(Signer::status)
                .containsExactly(SignerStatus.SIGNED, SignerStatus.SIGNED);
    }

    @Test
    void aRefusedFileAnswers422WithItsReasonAndChangesNothing() throws Exception {
        for (SignedUploadRejection rejection : SignedUploadRejection.values()) {
            FakeSignedPdfVerifier.force(rejection);
            mockMvc.perform(upload(tokenA, FakeSignedPdfVerifier.signed(source, "Client Martin")).with(ip()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value(rejection.name()));
        }
        FakeSignedPdfVerifier.reset();
        assertThat(uploads.findByRequestId(request.id())).isEmpty();
        assertThat(signer("Client").status()).isEqualTo(SignerStatus.PENDING);
    }

    @Test
    void aFileThatDoesNotExtendTheCurrentDocumentIsRefusedByTheVerifier() throws Exception {
        mockMvc.perform(upload(tokenA, "%PDF-another".getBytes()).with(ip()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOCUMENT_MISMATCH"));
    }

    @Test
    void theSignButtonIsRefusedWith409AndItsCodeWhileDeclineStaysPossible() throws Exception {
        mockMvc.perform(post("/api/portal/signature/sign").header(TOKEN, tokenA).with(ip()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIGNED_UPLOAD_REQUIRED"));
        assertThat(signer("Client").status()).isEqualTo(SignerStatus.PENDING);

        mockMvc.perform(post("/api/portal/signature/decline").header(TOKEN, tokenB).with(ip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void anOversizedFileAnswers413BeforeAnyVerification() throws Exception {
        FakeSignedPdfVerifier.force(SignedUploadRejection.NOT_A_PDF); // atteint seulement si la taille passait
        mockMvc.perform(upload(tokenA, new byte[130_000]).with(ip()))
                .andExpect(status().isPayloadTooLarge());
        assertThat(signer("Client").status()).isEqualTo(SignerStatus.PENDING);
    }

    @Test
    void everyDenialIsTheSameUniform404AndNeverReadsTheBody() throws Exception {
        byte[] pdf = FakeSignedPdfVerifier.signed(source, "Client Martin");
        String unknown = mockMvc.perform(upload("x".repeat(43), pdf).with(ip()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(upload(null, pdf).with(ip()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String inQuery = mockMvc.perform(upload(null, pdf).param("token", tokenA).with(ip()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        // jeton vivant d'un signataire révoqué : même réponse
        signatureRepository.updateSigner(signer("Garant").decided(SignerStatus.REVOKED, java.time.Instant.now(), null));
        String revoked = mockMvc.perform(upload(tokenB, pdf).with(ip()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(missing).isEqualTo(unknown);
        assertThat(inQuery).isEqualTo(unknown);
        assertThat(revoked).isEqualTo(unknown);
        assertThat(uploads.findByRequestId(request.id())).isEmpty();
    }

    @Test
    void aSignerWhoAlreadySignedCannotUploadAgain() throws Exception {
        mockMvc.perform(upload(tokenA, FakeSignedPdfVerifier.signed(source, "Client Martin")).with(ip()))
                .andExpect(status().isOk());

        mockMvc.perform(upload(tokenA, FakeSignedPdfVerifier.signed(source, "Client Martin")).with(ip()))
                .andExpect(status().isNotFound());
        assertThat(uploads.findByRequestId(request.id())).hasSize(1);
    }

    @Test
    void theSameCertificateCannotSignForTwoSigners() throws Exception {
        byte[] first = FakeSignedPdfVerifier.signed(source, "Meme Personne");
        mockMvc.perform(upload(tokenB, first).with(ip())).andExpect(status().isOk());

        mockMvc.perform(upload(tokenA, FakeSignedPdfVerifier.signed(first, "Meme Personne")).with(ip()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SIGNER"));
    }

    @Test
    void aStaleVersionIsRefusedAfterSomeoneElseSigned() throws Exception {
        // le client a téléchargé la source, le garant signe, puis le client téléverse sur la version dépassée
        byte[] staleBase = FakeSignedPdfVerifier.signed(source, "Client Martin");
        mockMvc.perform(upload(tokenB, FakeSignedPdfVerifier.signed(source, "Garant Dupont")).with(ip()))
                .andExpect(status().isOk());

        mockMvc.perform(upload(tokenA, staleBase).with(ip()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOCUMENT_MISMATCH"));
    }

    @Test
    void perIpRateLimitAppliesToTheUploadBeforeTheBodyIsRead() throws Exception {
        String addr = "10.51.0." + (1 + IP_SEQUENCE.getAndIncrement() % 250);
        RequestPostProcessor fixed = req -> {
            req.setRemoteAddr(addr);
            return req;
        };
        int refused = 0;
        for (int i = 0; i < 40; i++) {
            int code = mockMvc.perform(upload("z".repeat(43), new byte[2048]).with(fixed))
                    .andReturn().getResponse().getStatus();
            if (code == 429) {
                refused++;
            }
        }
        assertThat(refused).isGreaterThanOrEqualTo(10);
    }
}
