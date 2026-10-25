package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierPermission;
import be.myrmacta.domain.dossier.model.DossierTypeAclEntry;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * TODO §72, M6c — « Mes signatures » : signataires internes, séquentiel (S-INT-1/2/3/5/6, S-PW-1 côté
 * backend, S-DEC-5 interne), sur le moteur réel avec les vrais droits (ACL par type).
 */
@AutoConfigureMockMvc
class MySignaturesApiTest extends AbstractSignatureEngineTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DossierTypeAclRepository aclRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Dossier dossier;
    private String processInstanceId;
    private String reviewer;
    private String approver;
    private SignatureRequest request;

    @BeforeEach
    void setUp() {
        reviewer = "reviewer-" + System.nanoTime();
        approver = "approver-" + System.nanoTime();
        dossier = dossier(Map.of("reviewerId", reviewer, "approverId", approver));
        grant(reviewer, DossierPermission.VIEW);
        grant(approver, DossierPermission.VIEW);
        String ref = producedDoc(dossier.id());
        processInstanceId = runtimeService.startProcessInstanceByKey("signatureInternalSmoke",
                Map.of("dossierId", dossier.id().toString(), "generatedDocumentId", ref)).getId();
        request = signatureRepository.findByDossierId(dossier.id()).getFirst();
    }

    private void grant(String userId, DossierPermission permission) {
        aclRepository.save(new DossierTypeAclEntry("tenant_dev", dossier.dossierTypeCode(), userId, permission));
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

    @Test
    void eachUserSeesOnlyTheirOwnRequestsAndWhetherItIsTheirTurn() throws Exception {
        // S-INT-1
        mockMvc.perform(get("/api/signatures/mine").with(as(reviewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].myTurn").value(true))
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[0].simulated").value(true))
                .andExpect(jsonPath("$[0].documentLabel").value("Contrat"))
                .andExpect(jsonPath("$[0].dossierReference").value(dossier.reference()));
        mockMvc.perform(get("/api/signatures/mine").with(as(approver)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].myTurn").value(false));
        mockMvc.perform(get("/api/signatures/mine").with(as("someone-else")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void documentAndDecisionAreReservedToTheSignerOnHisTurn() throws Exception {
        // S-INT-2, S-INT-5
        Signer rev = signerOf(reviewer);
        Signer app = signerOf(approver);

        mockMvc.perform(get("/api/signatures/mine/{id}/document", rev.id()).with(as(reviewer)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(storage.retrieve(TENANT, request.sourceStorageRef())));
        // autre utilisateur, pas son tour, signataire inconnu : même 404
        mockMvc.perform(get("/api/signatures/mine/{id}/document", rev.id()).with(as(approver)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/signatures/mine/{id}/document", app.id()).with(as(approver)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/signatures/mine/{id}/sign", app.id()).with(as(approver)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/signatures/mine/{id}/sign", UUID.randomUUID()).with(as(reviewer)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/signatures/mine/{id}/sign", rev.id()).with(as(approver)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(signatureRepository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.PENDING);
    }

    @Test
    void sequentialInternalSigningCompletesTheRequestAndResumesTheProcess() throws Exception {
        // S-PW-1 (backend), S-DEC-5 interne
        Signer rev = signerOf(reviewer);
        Signer app = signerOf(approver);
        assertThat(rev.status()).isEqualTo(SignerStatus.PENDING);
        assertThat(app.status()).isEqualTo(SignerStatus.WAITING);

        mockMvc.perform(post("/api/signatures/mine/{id}/sign", rev.id()).with(as(reviewer)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"));
        assertThat(signerOf(approver).status()).isEqualTo(SignerStatus.PENDING);
        mockMvc.perform(get("/api/signatures/mine").with(as(approver)))
                .andExpect(jsonPath("$[0].myTurn").value(true));
        mockMvc.perform(get("/api/signatures/mine").with(as(reviewer)))
                .andExpect(jsonPath("$.length()").value(0)); // déjà signé

        mockMvc.perform(post("/api/signatures/mine/{id}/sign", app.id()).with(as(approver)).with(csrf()))
                .andExpect(status().isOk());

        assertThat(signatureRepository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).count()).isZero();
        assertThat(elementRepository.findByDossierIdOrderByAddedAtAsc(dossier.id()).stream()
                        .filter(e -> e.metadata().containsKey("signatureRole")))
                .hasSize(2);
        // S-DOC-2 : l'API existante des éléments expose le marqueur ; l'UI distingue source / signé / preuve
        mockMvc.perform(get("/api/dossiers/{id}/elements", dossier.id()).with(as(reviewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.metadata.signatureRole=='SIGNED_DOCUMENT')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.metadata.signatureRole=='SIGNATURE_PROOF')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.metadata.signatureRole=='SIGNED_DOCUMENT')].metadata.simulated")
                        .value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.content[?(@.label=='Contrat')].metadata.signatureRole").isEmpty());
    }

    @Test
    void declineWithReasonClosesTheRequestAsDeclined() throws Exception {
        Signer rev = signerOf(reviewer);
        mockMvc.perform(post("/api/signatures/mine/{id}/decline", rev.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Clause 4 à revoir\"}")
                        .with(as(reviewer)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        assertThat(signatureRepository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.DECLINED);
        assertThat(signerOf(reviewer).declineReason()).isEqualTo("Clause 4 à revoir");
        assertThat(signerOf(approver).status()).isEqualTo(SignerStatus.REVOKED);
    }

    @Test
    void readRightOnTheDossierIsRequired() throws Exception {
        // S-INT-3 : sans droit de lecture, la demande est invisible et inaccessible
        String noRights = "norights-" + System.nanoTime();
        // le signataire relecteur perd son droit : on le remplace par un utilisateur sans ACL
        Dossier other = dossier(Map.of("reviewerId", noRights, "approverId", approver));
        String ref = producedDoc(other.id());
        runtimeService.startProcessInstanceByKey("signatureInternalSmoke",
                Map.of("dossierId", other.id().toString(), "generatedDocumentId", ref));
        Signer mine = signatureRepository.findByDossierId(other.id()).stream()
                .flatMap(r -> signatureRepository.findSigners(r.id()).stream())
                .filter(s -> noRights.equals(s.userId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/signatures/mine").with(as(noRights)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/signatures/mine/{id}/document", mine.id()).with(as(noRights)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/signatures/mine/{id}/sign", mine.id()).with(as(noRights)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void closedDossierBlocksTheDecision() throws Exception {
        jdbcTemplate.update("update dossier set status = 'CLOTURE' where id = ?", dossier.id());
        Signer rev = signerOf(reviewer);

        mockMvc.perform(post("/api/signatures/mine/{id}/sign", rev.id()).with(as(reviewer)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(List.of(signerOf(reviewer).status())).containsExactly(SignerStatus.PENDING);
    }
}
