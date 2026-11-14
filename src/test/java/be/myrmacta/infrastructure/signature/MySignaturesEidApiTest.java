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
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.port.in.DispatchSignatureOutcomesUseCase;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * TODO §138 — « Mes signatures » avec le fournisseur {@code eid} (chaîne BFF, CSRF) : téléversement par
 * l'utilisateur concerné seulement, refus, taille, clic refusé ; et le parcours complet sur le moteur réel
 * (demande émise par le BPMN, deux signataires en séquence qui téléversent, demande {@code SIGNED}, document
 * signé et preuve rattachés par le répartiteur, process repris).
 */
@AutoConfigureMockMvc
class MySignaturesEidApiTest extends AbstractEidSignatureEngineTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DossierTypeAclRepository aclRepository;

    @Autowired
    private SignedUploadRepository uploads;

    @Autowired
    private DispatchSignatureOutcomesUseCase dispatcher;

    private Dossier dossier;
    private String processInstanceId;
    private String reviewer;
    private String approver;
    private SignatureRequest request;
    private byte[] source;

    @BeforeEach
    void setUp() {
        reviewer = "reviewer-" + System.nanoTime();
        approver = "approver-" + System.nanoTime();
        dossier = dossier(Map.of("reviewerId", reviewer, "approverId", approver));
        aclRepository.save(new DossierTypeAclEntry("tenant_dev", dossier.dossierTypeCode(), reviewer, DossierPermission.VIEW));
        aclRepository.save(new DossierTypeAclEntry("tenant_dev", dossier.dossierTypeCode(), approver, DossierPermission.VIEW));
        String ref = producedDoc(dossier.id());
        processInstanceId = runtimeService.startProcessInstanceByKey("signatureInternalSmoke",
                Map.of("dossierId", dossier.id().toString(), "generatedDocumentId", ref)).getId();
        request = signatureRepository.findByDossierId(dossier.id()).getFirst();
        source = storage.retrieve(TENANT, request.sourceStorageRef());
    }

    private static RequestPostProcessor as(String userId) {
        return oidcLogin()
                .idToken(t -> t.subject(userId).claim("tenant_id", "tenant_dev"))
                .authorities(new SimpleGrantedAuthority("ROLE_DOSSIER_MANAGER"));
    }

    private Signer signerOf(String userId) {
        return signatureRepository.findSigners(request.id()).stream()
                .filter(s -> userId.equals(s.userId()))
                .findFirst()
                .orElseThrow();
    }

    private MockMultipartHttpServletRequestBuilder upload(Signer signer, byte[] pdf) {
        return multipart("/api/signatures/mine/{id}/signed-document", signer.id())
                .file(new MockMultipartFile("file", "signe.pdf", "application/pdf", pdf));
    }

    @Test
    void theListAnnouncesTheUploadAndTheSignClickIsRefusedWith409() throws Exception {
        Signer rev = signerOf(reviewer);
        mockMvc.perform(get("/api/signatures/mine").with(as(reviewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].signedUpload").value(true))
                .andExpect(jsonPath("$[0].hosted").value(false))
                .andExpect(jsonPath("$[0].simulated").value(false));

        mockMvc.perform(post("/api/signatures/mine/{id}/sign", rev.id()).with(as(reviewer)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIGNED_UPLOAD_REQUIRED"));
        assertThat(signerOf(reviewer).status()).isEqualTo(SignerStatus.PENDING);

        mockMvc.perform(post("/api/signatures/mine/{id}/decline", rev.id()).with(as(reviewer)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void theUploadRequiresACsrfTokenAndAnAuthenticatedSession() throws Exception {
        Signer rev = signerOf(reviewer);
        byte[] pdf = FakeSignedPdfVerifier.signed(source, "Relecteur");

        mockMvc.perform(upload(rev, pdf).with(as(reviewer))).andExpect(status().isForbidden());
        int anonymous = mockMvc.perform(upload(rev, pdf).with(csrf())).andReturn().getResponse().getStatus();
        assertThat(anonymous).isIn(302, 401);
        assertThat(uploads.findByRequestId(request.id())).isEmpty();
    }

    @Test
    void anotherUserOrASignerWhoseTurnHasNotComeGetsTheUniform404() throws Exception {
        Signer rev = signerOf(reviewer);
        Signer app = signerOf(approver);
        byte[] pdf = FakeSignedPdfVerifier.signed(source, "Relecteur");

        mockMvc.perform(upload(rev, pdf).with(as(approver)).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(upload(app, pdf).with(as(approver)).with(csrf())).andExpect(status().isNotFound()); // WAITING
        mockMvc.perform(upload(app, pdf).with(as("someone-else")).with(csrf())).andExpect(status().isNotFound());
        assertThat(uploads.findByRequestId(request.id())).isEmpty();
    }

    @Test
    void aRefusedFileAnswers422WithItsReasonAndAnOversizedOneAnswers413() throws Exception {
        Signer rev = signerOf(reviewer);
        FakeSignedPdfVerifier.force(SignedUploadRejection.CERTIFICATE_NOT_QUALIFIED);
        mockMvc.perform(upload(rev, FakeSignedPdfVerifier.signed(source, "Relecteur")).with(as(reviewer)).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("CERTIFICATE_NOT_QUALIFIED"));
        FakeSignedPdfVerifier.reset();

        mockMvc.perform(upload(rev, new byte[130_000]).with(as(reviewer)).with(csrf()))
                .andExpect(status().isPayloadTooLarge());
        assertThat(uploads.findByRequestId(request.id())).isEmpty();
        assertThat(signerOf(reviewer).status()).isEqualTo(SignerStatus.PENDING);
    }

    @Test
    void twoSignersUploadInSequenceThenTheDispatcherAttachesTheFinalDocumentAndTheProofAndResumesTheProcess()
            throws Exception {
        Signer rev = signerOf(reviewer);
        Signer app = signerOf(approver);
        byte[] byReviewer = FakeSignedPdfVerifier.signed(source, "Relecteur Un");

        mockMvc.perform(upload(rev, byReviewer).with(as(reviewer)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"));
        assertThat(signerOf(approver).status()).isEqualTo(SignerStatus.PENDING);
        // l'approbateur reçoit le PDF déjà signé par le relecteur
        mockMvc.perform(get("/api/signatures/mine/{id}/document", app.id()).with(as(approver)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(byReviewer));

        byte[] byApprover = FakeSignedPdfVerifier.signed(byReviewer, "Approbateur Deux");
        mockMvc.perform(upload(app, byApprover).with(as(approver)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"));

        SignatureRequest closed = signatureRepository.findById(request.id()).orElseThrow();
        assertThat(closed.status()).isEqualTo(SignatureStatus.SIGNED);
        // le document final et la preuve sont produits par le répartiteur, pas par la décision
        assertThat(marked()).isEmpty();
        assertThat(runtimeService.getVariable(processInstanceId, "signatureOutcome")).isNull();

        assertThat(dispatcher.dispatch(request.id())).isTrue();

        List<DossierElement> marked = marked();
        assertThat(marked).hasSize(2).extracting(e -> e.metadata().get("signatureRole"))
                .containsExactly("SIGNED_DOCUMENT", "SIGNATURE_PROOF");
        assertThat(marked).allSatisfy(e -> {
            assertThat(e.metadata()).containsEntry("simulated", false);
            assertThat(e.label()).doesNotContain("SIMULATION");
        });
        assertThat(storage.retrieve(TENANT, marked.get(0).storageRef())).isEqualTo(byApprover);
        String proof = TestPdfs.text(storage.retrieve(TENANT, marked.get(1).storageRef()));
        assertThat(proof).contains("Relecteur Un", "Approbateur Deux", request.id().toString(),
                FakeSignedPdfVerifier.fingerprintOf("Relecteur Un"), "PAdES-BASELINE-B");
        assertThat(proof).doesNotContain("SIMULATION");
        // le process a repris et atteint sa fin
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).count()).isZero();

        // reprise idempotente : ni second document, ni seconde preuve
        dispatcher.dispatch(request.id());
        assertThat(marked()).hasSize(2);
    }

    private List<DossierElement> marked() {
        return elementRepository.findByDossierIdOrderByAddedAtAsc(dossier.id()).stream()
                .filter(e -> e.metadata().containsKey("signatureRole"))
                .toList();
    }
}
