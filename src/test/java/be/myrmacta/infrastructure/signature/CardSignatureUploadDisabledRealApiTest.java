package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.infrastructure.signature.eid.EidTestPdfs;
import be.myrmacta.infrastructure.signature.eid.EidTestPki;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.KeyType;
import be.myrmacta.infrastructure.web.portal.FixedClockPortalRateLimiterConfig;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

/**
 * TODO §138 — téléversement DÉSACTIVÉ (défaut) avec le vrai vérificateur : la signature dans la page (tests hérités)
 * fonctionne à l'identique ; les deux points de téléversement refusent par 409 {@code SIGNED_UPLOAD_DISABLED} après
 * le contrôle d'accès, sans lire le fichier ; le clic « Signer » reste refusé ; le document de travail est servi.
 */
@SpringBootTest(properties = {
        "app.signature.provider=eid",
        "app.signature.send.poll-delay=1h",
        "app.signature.expiry.poll-delay=1h",
        "app.signature.dispatch.poll-delay=1h",
        "app.signature.send.resend-min-interval=0s",
        "app.portal.enabled=true",
        "app.portal.public-base-url=https://ff.example.org/tenant-a",
        "app.portal.send.poll-delay=1h",
        "app.portal.expiry.poll-delay=1h",
        "flowable.async-executor-activate=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({OidcClientTestConfiguration.class, AbstractCardSignatureRealApiTest.RealVerifierConfig.class,
        FixedClockPortalRateLimiterConfig.class})
class CardSignatureUploadDisabledRealApiTest extends AbstractCardSignatureRealApiTest {

    @Override
    protected boolean uploadOffered() {
        return false;
    }

    @Test
    void portalUploadAnswers409WithItsCodeAfterTheAccessCheckAndNeverVerifiesTheFile() throws Exception {
        Portal p = portal();
        byte[] signed = EidTestPdfs.sign(p.source(), card);

        mockMvc.perform(multipart(PORTAL + "/signed-document")
                        .file(new MockMultipartFile("file", "s.pdf", "application/pdf", signed))
                        .header(TOKEN, p.tokenA()).with(ip()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SIGNED_UPLOAD_DISABLED"));
        // le refus uniforme garde la priorité : sans jeton valide, toujours le même 404
        mockMvc.perform(multipart(PORTAL + "/signed-document")
                        .file(new MockMultipartFile("file", "s.pdf", "application/pdf", signed))
                        .header(TOKEN, "u".repeat(43)).with(ip()))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart(PORTAL + "/signed-document")
                        .file(new MockMultipartFile("file", "s.pdf", "application/pdf", signed)).with(ip()))
                .andExpect(status().isNotFound());
        assertThat(uploads.findByRequestId(p.request().id())).isEmpty();
        assertThat(signatureRepository.findSigners(p.request().id()).getFirst().status()).isEqualTo(SignerStatus.PENDING);
    }

    @Test
    void internalUploadAnswers409WithItsCodeAndOtherUsersStillGetTheUniform404() throws Exception {
        Internal i = internal();
        Signer rev = signerOf(i, i.reviewer());
        byte[] signed = EidTestPdfs.sign(i.source(), card);
        String path = "/api/signatures/mine/" + rev.id() + "/signed-document";

        mockMvc.perform(multipart(path).file(new MockMultipartFile("file", "s.pdf", "application/pdf", signed))
                        .with(as(i.reviewer())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SIGNED_UPLOAD_DISABLED"));
        mockMvc.perform(multipart(path).file(new MockMultipartFile("file", "s.pdf", "application/pdf", signed))
                        .with(as(i.approver())).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(uploads.findByRequestId(i.request().id())).isEmpty();
    }

    @Test
    void theSignClickStaysRefusedAndTheCurrentDocumentIsStillServed() throws Exception {
        Portal p = portal();

        mockMvc.perform(post(PORTAL + "/sign").header(TOKEN, p.tokenA()).with(ip()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIGNED_UPLOAD_REQUIRED"));
        mockMvc.perform(get(PORTAL + "/document").header(TOKEN, p.tokenA()).with(ip()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(p.source()));
        // le document de travail avance avec la signature dans la page, téléversement coupé
        signInPage(p.tokenB(), PKI.qualifiedSigner(KeyType.RSA_2048));
        byte[] advanced = mockMvc.perform(get(PORTAL + "/document").header(TOKEN, p.tokenA()).with(ip()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(advanced.length).isGreaterThan(p.source().length);
        assertThat(signatureRepository.findById(p.request().id()).orElseThrow().status()).isEqualTo(SignatureStatus.PENDING);
    }
}
