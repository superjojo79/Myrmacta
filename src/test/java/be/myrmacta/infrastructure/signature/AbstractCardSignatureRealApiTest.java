package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierPermission;
import be.myrmacta.domain.dossier.model.DossierTypeAclEntry;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.signature.SignatureWorkingDocuments;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import be.myrmacta.infrastructure.signature.eid.DssSignedPdfVerifier;
import be.myrmacta.infrastructure.signature.eid.EidTestPdfs;
import be.myrmacta.infrastructure.signature.eid.EidTestPki;
import be.myrmacta.infrastructure.signature.eid.EidTestPki.KeyType;
import be.myrmacta.infrastructure.signature.eid.SignedPdfVerifier;
import be.myrmacta.infrastructure.web.portal.FixedClockPortalRateLimiterConfig;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.DigestInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §138 (D-EID-BRIDGE) — de bout en bout avec le VRAI vérificateur (DSS) et le VRAI assembleur, sans faux : PKI
 * de test, fichier d'autorités écrit par le test, révocation servie en mémoire. Les points d'API
 * {@code card-signature/prepare} et {@code complete} (portail par jeton, « Mes signatures » avec CSRF), puis le
 * téléversement d'un PDF signé par {@link EidTestPdfs}. La carte est simulée : le condensé reçu est signé avec la clé
 * de test, sans le recalculer.
 */
abstract class AbstractCardSignatureRealApiTest extends AbstractSignatureEngineTest {

    protected static final EidTestPki PKI = EidTestPki.create("Card Http");
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();
    protected static final String TOKEN = "X-Portal-Token";
    protected static final String PORTAL = "/api/portal/signature";
    private static Path anchors;

    static {
        try {
            anchors = Files.createTempFile("eid-anchors", ".pem");
            StringBuilder pem = new StringBuilder();
            for (X509Certificate certificate : PKI.trustedFile()) {
                pem.append("-----BEGIN CERTIFICATE-----\n")
                        .append(Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                                .encodeToString(certificate.getEncoded()))
                        .append("\n-----END CERTIFICATE-----\n");
            }
            Files.writeString(anchors, pem.toString());
            anchors.toFile().deleteOnExit();
        } catch (IOException | CertificateEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void trust(DynamicPropertyRegistry registry) {
        registry.add("app.signature.eid.trust-anchors-file", () -> anchors.toString());
    }

    /** Le vrai vérificateur, horloge et révocation réelles côté temps, hiérarchie de test en mémoire. */
    @TestConfiguration
    static class RealVerifierConfig {
        @Bean
        @Primary
        SignedPdfVerifier realVerifier() {
            return new DssSignedPdfVerifier(PKI.trustedFile(), Clock.systemUTC(),
                    PKI.transport().clock(Clock.systemUTC()));
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    protected SignedUploadRepository uploads;

    @Autowired
    private DossierTypeAclRepository aclRepository;

    protected EidTestPki.Signer card;

    /** Le téléversement d'un PDF signé est offert dans ce contexte (propriété {@code upload-enabled}). */
    protected abstract boolean uploadOffered();

    @BeforeEach
    void setUp() {
        card = PKI.qualifiedSigner(KeyType.RSA_2048);
    }

    // ---------- outils ----------

    protected static RequestPostProcessor ip() {
        int n = IP_SEQUENCE.getAndIncrement();
        String addr = "10.60." + (n / 250 % 250) + "." + (1 + n % 250);
        return req -> {
            req.setRemoteAddr(addr);
            return req;
        };
    }

    protected static RequestPostProcessor as(String userId) {
        return oidcLogin()
                .idToken(t -> t.subject(userId).claim("tenant_id", "tenant_dev"))
                .authorities(new SimpleGrantedAuthority("ROLE_DOSSIER_MANAGER"));
    }

    protected static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    protected static String cert(EidTestPki.Signer signer) throws Exception {
        return b64(signer.certificate().getEncoded());
    }

    private static List<String> chain() throws Exception {
        return List.of(b64(PKI.intermediate().getEncoded()));
    }

    private String realProducedDoc(UUID dossierId) {
        String ref = storage.store(TENANT, "application/pdf", EidTestPdfs.basePdf());
        elementRepository.save(new DossierElement(UUID.randomUUID(), dossierId, TENANT, ElementType.PRODUCED_DOC, "Contrat",
                ref, Map.of("contentType", "application/pdf"), "system", Instant.now(), null));
        return ref;
    }

    /** Ce que fait la carte : signer le condensé reçu, sans le recalculer (RSA PKCS#1 v1.5, SHA-256). */
    private static byte[] cardSign(EidTestPki.Signer signer, byte[] digest) throws Exception {
        byte[] digestInfo = new DigestInfo(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE), digest)
                .getEncoded();
        Signature rsa = Signature.getInstance("NONEwithRSA");
        rsa.initSign(signer.key());
        rsa.update(digestInfo);
        return rsa.sign();
    }

    private String body(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return json.writeValueAsString(map);
    }

    private JsonNode prepare(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) throws Exception {
        String response = mockMvc.perform(builder.contentType(MediaType.APPLICATION_JSON)
                        .content(body("certificate", cert(card), "certificateChain", chain())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashFunction").value("SHA-256"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private String complete(JsonNode prepared, byte[] signature, String signingTimeOverride, String digestOverride)
            throws Exception {
        return body("certificate", cert(card), "certificateChain", chain(),
                "signingTime", signingTimeOverride != null ? signingTimeOverride : prepared.get("signingTime").asString(),
                "documentDigest", digestOverride != null ? digestOverride : prepared.get("documentDigest").asString(),
                "signature", b64(signature));
    }

    /** Signature dans la page de bout en bout par le portail : prepare, signature par la « carte », complete. */
    protected final void signInPage(String token, EidTestPki.Signer signer) throws Exception {
        String certificate = cert(signer);
        JsonNode prepared = json.readTree(mockMvc.perform(post(PORTAL + "/card-signature/prepare")
                        .header(TOKEN, token).with(ip()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("certificate", certificate, "certificateChain", chain())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        byte[] signature = cardSign(signer, Base64.getDecoder().decode(prepared.get("digest").asString()));
        mockMvc.perform(post(PORTAL + "/card-signature/complete").header(TOKEN, token).with(ip())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("certificate", certificate, "certificateChain", chain(),
                                "signingTime", prepared.get("signingTime").asString(),
                                "documentDigest", prepared.get("documentDigest").asString(),
                                "signature", b64(signature))))
                .andExpect(status().isOk());
    }

    protected record Portal(UUID dossierId, SignatureRequest request, String tokenA, String tokenB, byte[] source) {}

    protected Portal portal() {
        String mailA = "client-" + System.nanoTime() + "@example.org";
        String mailB = "garant-" + System.nanoTime() + "@example.org";
        UUID dossierId = dossier(Map.of("signerA", mailA, "signerB", mailB)).id();
        startSignatureSmoke(dossierId, realProducedDoc(dossierId));
        String tokenA = sendAndCaptureToken(mailA);
        String tokenB = sendAndCaptureToken(mailB);
        SignatureRequest request = signatureRepository.findByDossierId(dossierId).getFirst();
        return new Portal(dossierId, request, tokenA, tokenB, storage.retrieve(TENANT, request.sourceStorageRef()));
    }

    // ---------- portail ----------

    @Test
    void portalSignsInThePageWithTheRealAssemblerAndTheRealVerifier() throws Exception {
        Portal p = portal();
        mockMvc.perform(get(PORTAL).header(TOKEN, p.tokenA()).with(ip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedUpload").value(uploadOffered()))
                .andExpect(jsonPath("$.cardBridges.length()").value(1))
                .andExpect(jsonPath("$.cardBridges[0]").value("web-eid"));

        JsonNode prepared = prepare(post(PORTAL + "/card-signature/prepare").header(TOKEN, p.tokenA()).with(ip()));
        assertThat(prepared.get("documentDigest").asString()).isEqualTo(SignatureWorkingDocuments.digestOf(p.source()));
        assertThat(Instant.parse(prepared.get("signingTime").asString()).getNano()).isZero();
        assertThat(uploads.findByRequestId(p.request().id())).isEmpty(); // aucune écriture

        byte[] signature = cardSign(card, Base64.getDecoder().decode(prepared.get("digest").asString()));
        mockMvc.perform(post(PORTAL + "/card-signature/complete").header(TOKEN, p.tokenA()).with(ip())
                        .contentType(MediaType.APPLICATION_JSON).content(complete(prepared, signature, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"));

        assertThat(signatureRepository.findSigners(p.request().id()).getFirst().status()).isEqualTo(SignerStatus.SIGNED);
        assertThat(signatureRepository.findById(p.request().id()).orElseThrow().status()).isEqualTo(SignatureStatus.SIGNED);
        var saved = uploads.findByRequestId(p.request().id());
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().evidence().signatureFormat()).isEqualTo("PAdES-BASELINE-B");
        assertThat(saved.getFirst().evidence().qualifiedDevice()).isTrue();
        // le document de travail a avancé : il prolonge la source octet pour octet
        byte[] working = storage.retrieve(TENANT, saved.getFirst().storageRef());
        assertThat(working.length).isGreaterThan(p.source().length);
        assertThat(Arrays.mismatch(p.source(), Arrays.copyOf(working, p.source().length))).isEqualTo(-1);
    }

    @Test
    void theDocumentChangedBetweenPrepareAndCompleteIsRefusedWithStaleDocument() throws Exception {
        Portal p = portal();
        JsonNode prepared = prepare(post(PORTAL + "/card-signature/prepare").header(TOKEN, p.tokenA()).with(ip()));
        // le garant (facultatif) signe entre-temps dans la page, avec un autre certificat : le document courant change
        signInPage(p.tokenB(), PKI.qualifiedSigner(KeyType.RSA_2048));

        byte[] signature = cardSign(card, Base64.getDecoder().decode(prepared.get("digest").asString()));
        mockMvc.perform(post(PORTAL + "/card-signature/complete").header(TOKEN, p.tokenA()).with(ip())
                        .contentType(MediaType.APPLICATION_JSON).content(complete(prepared, signature, null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("STALE_DOCUMENT"));
    }

    @Test
    void aSessionOlderThanFifteenMinutesOrInTheFutureAnswers422WithItsCode() throws Exception {
        Portal p = portal();
        JsonNode prepared = prepare(post(PORTAL + "/card-signature/prepare").header(TOKEN, p.tokenA()).with(ip()));
        byte[] signature = cardSign(card, Base64.getDecoder().decode(prepared.get("digest").asString()));

        for (String time : List.of(Instant.now().minusSeconds(3600).toString(), Instant.now().plusSeconds(3600).toString())) {
            mockMvc.perform(post(PORTAL + "/card-signature/complete").header(TOKEN, p.tokenA()).with(ip())
                            .contentType(MediaType.APPLICATION_JSON).content(complete(prepared, signature, time, null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("SIGNING_SESSION_EXPIRED"));
        }
        assertThat(uploads.findByRequestId(p.request().id())).isEmpty();
    }

    @Test
    void aWrongSignatureValueIsRefusedByTheVerifierWith422() throws Exception {
        Portal p = portal();
        JsonNode prepared = prepare(post(PORTAL + "/card-signature/prepare").header(TOKEN, p.tokenA()).with(ip()));
        byte[] wrong = cardSign(card, new byte[32]);

        mockMvc.perform(post(PORTAL + "/card-signature/complete").header(TOKEN, p.tokenA()).with(ip())
                        .contentType(MediaType.APPLICATION_JSON).content(complete(prepared, wrong, null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SIGNATURE_INVALID"));
        assertThat(signatureRepository.findSigners(p.request().id()).getFirst().status()).isEqualTo(SignerStatus.PENDING);
    }

    @Test
    void unreadableInputAnswers400WithoutDetail() throws Exception {
        Portal p = portal();
        String[] bodies = {
                body("certificate", "%%%not base64%%%"),
                body("certificateChain", chain()),
                body("certificate", b64("not a certificate".getBytes(StandardCharsets.UTF_8))),
                body("certificate", cert(card), "certificateChain", List.of("@@@")),
                "{ not json"
        };
        for (String bad : bodies) {
            String response = mockMvc.perform(post(PORTAL + "/card-signature/prepare").header(TOKEN, p.tokenA()).with(ip())
                            .contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("not a certificate").doesNotContain("Exception");
        }
        mockMvc.perform(post(PORTAL + "/card-signature/complete").header(TOKEN, p.tokenA()).with(ip())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("certificate", cert(card), "signingTime", Instant.now().toString(),
                                "documentDigest", "x", "signature", "***")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void everyAccessDenialIsTheSameUniform404() throws Exception {
        Portal p = portal();
        String ok = body("certificate", cert(card));
        String done = body("certificate", cert(card), "signingTime", Instant.now().toString(), "documentDigest", "x",
                "signature", b64(new byte[] {1}));
        String unknown = mockMvc.perform(post(PORTAL + "/card-signature/prepare").header(TOKEN, "u".repeat(43)).with(ip())
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(post(PORTAL + "/card-signature/prepare").with(ip())
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String completeUnknown = mockMvc.perform(post(PORTAL + "/card-signature/complete").header(TOKEN, "u".repeat(43)).with(ip())
                        .contentType(MediaType.APPLICATION_JSON).content(done))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        // un signataire révoqué : même réponse
        signatureRepository.updateSigner(signatureRepository.findSigners(p.request().id()).get(1)
                .decided(SignerStatus.REVOKED, Instant.now(), null));
        String revoked = mockMvc.perform(post(PORTAL + "/card-signature/prepare").header(TOKEN, p.tokenB()).with(ip())
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(List.of(missing, completeUnknown, revoked)).allSatisfy(b -> assertThat(b).isEqualTo(unknown));
    }

    @Test
    void perIpRateLimitAppliesToTheCardSignaturePoints() throws Exception {
        String addr = "10.61.0." + (1 + IP_SEQUENCE.getAndIncrement() % 250);
        RequestPostProcessor fixed = req -> {
            req.setRemoteAddr(addr);
            return req;
        };
        int refused = 0;
        for (int i = 0; i < 40; i++) {
            int code = mockMvc.perform(post(PORTAL + "/card-signature/prepare").header(TOKEN, "z".repeat(43)).with(fixed)
                            .contentType(MediaType.APPLICATION_JSON).content(body("certificate", "AAAA")))
                    .andReturn().getResponse().getStatus();
            if (code == 429) {
                refused++;
            }
        }
        assertThat(refused).isGreaterThanOrEqualTo(10);
    }

    @Test
    void aCertificateAndAFiveCertificateChainFitInThePortalJsonBodyLimit() throws Exception {
        // le filtre de taille du portail plafonne les corps JSON à 64 Kio (marge : un certificat eID pèse ~2 Kio)
        List<String> five = java.util.Collections.nCopies(5, b64(PKI.intermediate().getEncoded()));
        String request = body("certificate", cert(card), "certificateChain", five, "signingTime", Instant.now().toString(),
                "documentDigest", "0".repeat(64), "signature", b64(new byte[512]));

        assertThat(request.getBytes(StandardCharsets.UTF_8).length).isLessThan(64 * 1024 / 2);
    }

    // ---------- « Mes signatures » ----------

    protected record Internal(Dossier dossier, SignatureRequest request, String reviewer, String approver, byte[] source) {}

    protected Internal internal() {
        String reviewer = "reviewer-" + System.nanoTime();
        String approver = "approver-" + System.nanoTime();
        Dossier dossier = dossier(Map.of("reviewerId", reviewer, "approverId", approver));
        aclRepository.save(new DossierTypeAclEntry("tenant_dev", dossier.dossierTypeCode(), reviewer, DossierPermission.VIEW));
        aclRepository.save(new DossierTypeAclEntry("tenant_dev", dossier.dossierTypeCode(), approver, DossierPermission.VIEW));
        String ref = realProducedDoc(dossier.id());
        runtimeService.startProcessInstanceByKey("signatureInternalSmoke",
                Map.of("dossierId", dossier.id().toString(), "generatedDocumentId", ref));
        SignatureRequest request = signatureRepository.findByDossierId(dossier.id()).getFirst();
        return new Internal(dossier, request, reviewer, approver, storage.retrieve(TENANT, request.sourceStorageRef()));
    }

    protected Signer signerOf(Internal i, String userId) {
        return signatureRepository.findSigners(i.request().id()).stream()
                .filter(s -> userId.equals(s.userId())).findFirst().orElseThrow();
    }

    @Test
    void internalSignerSignsInThePageInSequenceWithCsrfAndTheRealVerifier() throws Exception {
        Internal i = internal();
        Signer rev = signerOf(i, i.reviewer());
        Signer app = signerOf(i, i.approver());
        String base = "/api/signatures/mine/" + rev.id() + "/card-signature";
        mockMvc.perform(get("/api/signatures/mine").with(as(i.reviewer())))
                .andExpect(jsonPath("$[0].signedUpload").value(uploadOffered()))
                .andExpect(jsonPath("$[0].cardBridges[0]").value("web-eid"));

        JsonNode prepared = prepare(post(base + "/prepare").with(as(i.reviewer())).with(csrf()));
        byte[] signature = cardSign(card, Base64.getDecoder().decode(prepared.get("digest").asString()));
        mockMvc.perform(post(base + "/complete").with(as(i.reviewer())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(complete(prepared, signature, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"));
        assertThat(signerOf(i, i.reviewer()).status()).isEqualTo(SignerStatus.SIGNED);
        assertThat(signerOf(i, i.approver()).status()).isEqualTo(SignerStatus.PENDING);

        // l'approbateur signe par-dessus : le document de travail a avancé, la demande se clôt
        EidTestPki.Signer second = PKI.qualifiedSigner(KeyType.EC_P384);
        JsonNode secondPrepared = json.readTree(mockMvc.perform(
                        post("/api/signatures/mine/" + app.id() + "/card-signature/prepare").with(as(i.approver())).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("certificate", b64(second.certificate().getEncoded()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hashFunction").value("SHA-384"))
                .andReturn().getResponse().getContentAsString());
        assertThat(secondPrepared.get("documentDigest").asString())
                .isNotEqualTo(SignatureWorkingDocuments.digestOf(i.source()));
    }

    @Test
    void internalCardSignatureRequiresCsrfAnAuthenticatedSessionAndTheRightUser() throws Exception {
        Internal i = internal();
        Signer rev = signerOf(i, i.reviewer());
        Signer app = signerOf(i, i.approver());
        String base = "/api/signatures/mine/" + rev.id() + "/card-signature";
        String ok = body("certificate", cert(card));

        mockMvc.perform(post(base + "/prepare").with(as(i.reviewer())).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(base + "/complete").with(as(i.reviewer())).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isForbidden());
        int anonymous = mockMvc.perform(post(base + "/prepare").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andReturn().getResponse().getStatus();
        assertThat(anonymous).isIn(302, 401);
        // un autre utilisateur, un signataire pas à son tour, un signataire inconnu : le même 404
        mockMvc.perform(post(base + "/prepare").with(as(i.approver())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/signatures/mine/" + app.id() + "/card-signature/prepare").with(as(i.approver())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/signatures/mine/" + UUID.randomUUID() + "/card-signature/complete")
                        .with(as(i.reviewer())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("certificate", cert(card), "signingTime", Instant.now().toString(),
                                "documentDigest", "x", "signature", b64(new byte[] {1}))))
                .andExpect(status().isNotFound());
        // entrée illisible par la voie authentifiée : 400
        mockMvc.perform(post(base + "/prepare").with(as(i.reviewer())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("certificate", "%%%")))
                .andExpect(status().isBadRequest());
        assertThat(uploads.findByRequestId(i.request().id())).isEmpty();
    }
}
