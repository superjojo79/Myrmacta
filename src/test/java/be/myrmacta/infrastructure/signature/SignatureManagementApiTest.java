package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import be.myrmacta.domain.signature.port.in.ExpireSignatureRequestsUseCase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * TODO §72, M7 — gestion instructeur (S-ADM-1/2/3/4), expiration (S-EXP-1, S-PROG-3), annulation sur
 * disparition du process (S-ADM-3), sur le moteur réel.
 */
@AutoConfigureMockMvc
class SignatureManagementApiTest extends AbstractSignatureEngineTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DossierTypeAclRepository aclRepository;

    @Autowired
    private ExpireSignatureRequestsUseCase expireUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Dossier dossier;
    private String processInstanceId;
    private String mailClient;
    private String tokenClient;
    private SignatureRequest request;

    @BeforeEach
    void setUp() {
        mailClient = "client-" + System.nanoTime() + "@example.org";
        String mailGarant = "garant-" + System.nanoTime() + "@example.org";
        dossier = dossier(Map.of("signerA", mailClient, "signerB", mailGarant));
        processInstanceId = startSignatureSmoke(dossier.id(), producedDoc(dossier.id()));
        tokenClient = sendAndCaptureToken(mailClient);
        sendAndCaptureToken(mailGarant);
        request = signatureRepository.findByDossierId(dossier.id()).getFirst();
    }

    private static RequestPostProcessor user(String userId) {
        return oidcLogin()
                .idToken(t -> t.subject(userId).claim("tenant_id", "tenant_dev"))
                .authorities(new SimpleGrantedAuthority("ROLE_DOSSIER_MANAGER"));
    }

    private String grant(DossierPermission permission) {
        String userId = "u-" + System.nanoTime();
        aclRepository.save(new DossierTypeAclEntry("tenant_dev", dossier.dossierTypeCode(), userId, permission));
        return userId;
    }

    private String taskName() {
        return taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult().getName();
    }

    @Test
    void viewSeesStatusesButNoContactEditSeesTheMaskedContact() throws Exception {
        // S-ADM-1
        String viewer = grant(DossierPermission.VIEW);
        String editor = grant(DossierPermission.EDIT);

        mockMvc.perform(get("/api/dossiers/{id}/signature-requests", dossier.id()).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(false))
                .andExpect(jsonPath("$.requests[0].status").value("PENDING"))
                .andExpect(jsonPath("$.requests[0].simulated").value(true))
                .andExpect(jsonPath("$.requests[0].signers[0].displayName").value("Client"))
                .andExpect(jsonPath("$.requests[0].signers[0].contact").doesNotExist());

        String body = mockMvc.perform(get("/api/dossiers/{id}/signature-requests", dossier.id()).with(user(editor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(true))
                .andExpect(jsonPath("$.requests[0].signers[0].contact").value(
                        org.hamcrest.Matchers.containsString("***@")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(mailClient);
    }

    @Test
    void noAccessAtAllIsForbidden() throws Exception {
        mockMvc.perform(get("/api/dossiers/{id}/signature-requests", dossier.id()).with(user("stranger-" + System.nanoTime())))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelNeedsEditPropagatesAndResumesTheProcessWithCancelled() throws Exception {
        // S-ADM-2
        String viewer = grant(DossierPermission.VIEW);
        String editor = grant(DossierPermission.EDIT);
        mockMvc.perform(post("/api/signature-requests/{id}/cancel", request.id()).with(user(viewer)).with(csrf()))
                .andExpect(status().isForbidden());
        // dryRun : contrôle sans effet
        mockMvc.perform(post("/api/signature-requests/{id}/cancel", request.id()).param("dryRun", "true")
                        .with(user(editor)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(signatureRepository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.PENDING);

        mockMvc.perform(post("/api/signature-requests/{id}/cancel", request.id()).with(user(editor)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.statusReason").value("CANCELLED_BY_USER"));

        assertThat(signatureRepository.findSigners(request.id())).extracting(Signer::status)
                .containsOnly(SignerStatus.REVOKED);
        assertThat(runtimeService.getVariable(processInstanceId, "signatureOutcome")).isEqualTo("CANCELLED");
        assertThat(taskName()).isEqualTo("Signature non obtenue");
        mockMvc.perform(post("/api/portal/signature/sign").header("X-Portal-Token", tokenClient))
                .andExpect(status().isNotFound());
        // une demande déjà close ne peut plus être annulée
        mockMvc.perform(post("/api/signature-requests/{id}/cancel", request.id()).with(user(editor)).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void resendInvalidatesTheOldTokenImmediatelyAndTheJobIssuesANewOne() throws Exception {
        // S-ADM-4
        String editor = grant(DossierPermission.EDIT);
        Signer client = signatureRepository.findSigners(request.id()).getFirst();
        String hashBefore = client.tokenHash();

        mockMvc.perform(post("/api/signature-requests/{id}/signers/{sid}/resend", request.id(), client.id())
                        .with(user(editor)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/portal/signature").header("X-Portal-Token", tokenClient))
                .andExpect(status().isNotFound());
        assertThat(signatureRepository.findSignerById(client.id()).orElseThrow().tokenHash()).isNull();

        String newToken = sendAndCaptureTokenAgain(mailClient);
        assertThat(newToken).isNotEqualTo(tokenClient);
        assertThat(signatureRepository.findSignerById(client.id()).orElseThrow().tokenHash()).isNotEqualTo(hashBefore);
        mockMvc.perform(get("/api/portal/signature").header("X-Portal-Token", newToken)).andExpect(status().isOk());
        verify(emailPort, atLeast(2)).send(eq(mailClient), anyString(), anyString());
    }

    private String sendAndCaptureTokenAgain(String recipient) {
        sendInvites.sendPending();
        org.mockito.ArgumentCaptor<String> body = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailPort, atLeast(2)).send(eq(recipient), anyString(), body.capture());
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/portal/#signature:([A-Za-z0-9_-]{43})")
                .matcher(body.getValue());
        if (!m.find()) {
            throw new AssertionError("no link");
        }
        return m.group(1);
    }

    @Test
    void resendRequiresEditAndAPendingSigner() throws Exception {
        String viewer = grant(DossierPermission.VIEW);
        Signer client = signatureRepository.findSigners(request.id()).getFirst();
        mockMvc.perform(post("/api/signature-requests/{id}/signers/{sid}/resend", request.id(), client.id())
                        .with(user(viewer)).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/signature-requests/{id}/signers/{sid}/resend", request.id(), UUID.randomUUID())
                        .with(user(grant(DossierPermission.EDIT))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletedProcessCancelsItsOpenRequestsAndInvalidatesTheLinks() throws Exception {
        // S-ADM-3
        runtimeService.deleteProcessInstance(processInstanceId, "annulé");

        SignatureRequest stored = signatureRepository.findById(request.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(SignatureStatus.CANCELLED);
        assertThat(stored.statusReason()).isEqualTo("PROCESS_GONE");
        assertThat(stored.outcomeDispatchedAt()).isNotNull(); // rien à reprendre
        mockMvc.perform(get("/api/portal/signature").header("X-Portal-Token", tokenClient))
                .andExpect(status().isNotFound());
    }

    @Test
    void expiryJobExpiresOverdueRequestsAndResumesTheProcess() throws Exception {
        // S-EXP-1, S-PROG-3 (intégration)
        jdbcTemplate.update("update signature_request set deadline_at = ? where id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), request.id());

        assertThat(expireUseCase.expireDue()).isGreaterThanOrEqualTo(1);
        assertThat(expireUseCase.expireDue()).isZero(); // idempotent

        assertThat(signatureRepository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.EXPIRED);
        List<Signer> signers = signatureRepository.findSigners(request.id());
        assertThat(signers).extracting(Signer::status).containsOnly(SignerStatus.EXPIRED);
        assertThat(runtimeService.getVariable(processInstanceId, "signatureOutcome")).isEqualTo("EXPIRED");
        assertThat(taskName()).isEqualTo("Signature non obtenue");
        mockMvc.perform(get("/api/portal/signature").header("X-Portal-Token", tokenClient))
                .andExpect(status().isNotFound());
    }
}
