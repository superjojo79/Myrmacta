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
 * TODO §138 — téléversement ACTIVÉ ({@code app.signature.eid.upload-enabled=true}) avec le vrai vérificateur : le
 * comportement d'avant ; la signature dans la page (tests hérités) fonctionne de la même façon.
 */
@SpringBootTest(properties = {
        "app.signature.provider=eid",
        "app.signature.eid.upload-enabled=true",
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
class CardSignatureUploadEnabledRealApiTest extends AbstractCardSignatureRealApiTest {

    @Override
    protected boolean uploadOffered() {
        return true;
    }

    @Test
    void uploadOfARealSignedPdfIsAcceptedAndAnUnqualifiedOrUntrustedCertificateIsRefused() throws Exception {
        Portal p = portal();
        EidTestPki.Signer unqualified = PKI.signer().withoutQcSscd().issue();
        EidTestPki.Signer stranger = EidTestPki.create("Stranger Http").qualifiedSigner(KeyType.RSA_2048);
        for (Map.Entry<EidTestPki.Signer, String> refused : Map.of(
                unqualified, "CERTIFICATE_NOT_QUALIFIED", stranger, "CERTIFICATE_UNTRUSTED").entrySet()) {
            mockMvc.perform(multipart(PORTAL + "/signed-document")
                            .file(new MockMultipartFile("file", "s.pdf", "application/pdf",
                                    EidTestPdfs.sign(p.source(), refused.getKey())))
                            .header(TOKEN, p.tokenA()).with(ip()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value(refused.getValue()));
        }

        mockMvc.perform(multipart(PORTAL + "/signed-document")
                        .file(new MockMultipartFile("file", "s.pdf", "application/pdf", EidTestPdfs.sign(p.source(), card)))
                        .header(TOKEN, p.tokenA()).with(ip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"));
        assertThat(uploads.findByRequestId(p.request().id())).hasSize(1);
    }
}
