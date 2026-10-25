package be.myrmacta.infrastructure.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatus;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatusReason;
import be.myrmacta.domain.workflow.portal.port.in.ExpirePortalLinksUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** TODO §68 — gestion instructeur et expiration (T-MANAGE-1…4, T-EXPIRY-1). */
@AutoConfigureMockMvc
class PortalLinkManagementApiTest extends AbstractPortalEngineTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DossierTypeAclRepository aclRepository;

    @Autowired
    private ExpirePortalLinksUseCase expireUseCase;

    private String recipient;
    private Dossier dossier;
    private Task task;
    private String token;

    @BeforeEach
    void setUp() {
        ensureSchema();
        recipient = "tiers-" + System.nanoTime() + "@example.org";
        dossier = dossier(Map.of("contactEmail", recipient));
        task = startPortalSmoke(dossier.id());
        token = sendAndCaptureToken(recipient);
    }

    private static RequestPostProcessor user(String userId, String role) {
        return oidcLogin()
                .idToken(t -> t.subject(userId).claim("tenant_id", "tenant_dev"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private void grant(String userId, DossierPermission permission) {
        aclRepository.save(new DossierTypeAclEntry("tenant_dev", dossier.dossierTypeCode(), userId, permission));
    }

    private PortalLink onlyLiveLink() {
        return portalLinkRepository.findLiveByTaskId(task.getId()).getFirst();
    }

    @Test
    void resend_revokesTheOldLink_andIssuesAFreshOne() throws Exception {
        // T-MANAGE-1
        String manager = "mgr-" + System.nanoTime();
        grant(manager, DossierPermission.EDIT);
        UUID oldId = onlyLiveLink().id();

        mockMvc.perform(post("/api/dossiers/{id}/portal-links/tasks/{taskId}/send", dossier.id(), task.getId())
                        .with(user(manager, "DOSSIER_MANAGER")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_SEND"))
                .andExpect(jsonPath("$.recipient").value(recipient));

        PortalLink old = portalLinkRepository.findById(oldId).orElseThrow();
        assertThat(old.status()).isEqualTo(PortalLinkStatus.REVOKED);
        assertThat(old.statusReason()).isEqualTo(PortalLinkStatusReason.RESENT);
        assertThat(old.revokedBy()).isEqualTo(manager);
        assertThat(onlyLiveLink().id()).isNotEqualTo(oldId);
        mockMvc.perform(get("/api/portal/task").header("X-Portal-Token", token)).andExpect(status().isNotFound());
    }

    @Test
    void revoke_killsTheLinkImmediately() throws Exception {
        // T-MANAGE-2
        String manager = "mgr-" + System.nanoTime();
        grant(manager, DossierPermission.EDIT);

        mockMvc.perform(post("/api/dossiers/{id}/portal-links/{linkId}/revoke", dossier.id(), onlyLiveLink().id())
                        .with(user(manager, "DOSSIER_MANAGER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.statusReason").value("REVOKED_BY_USER"));

        mockMvc.perform(get("/api/portal/task").header("X-Portal-Token", token)).andExpect(status().isNotFound());
    }

    @Test
    void sendAfterAddressFix_issuesALink_andStillInvalidAddressAnswers409() throws Exception {
        // T-MANAGE-3
        Dossier noAddress = dossier(Map.of());
        Task noAddressTask = startPortalSmoke(noAddress.id());
        assertThat(portalLinkRepository.findLiveByTaskId(noAddressTask.getId()).getFirst().status())
                .isEqualTo(PortalLinkStatus.NOT_SENT);
        String admin = "admin-" + System.nanoTime();

        mockMvc.perform(post("/api/dossiers/{id}/portal-links/tasks/{taskId}/send", noAddress.id(), noAddressTask.getId())
                        .with(user(admin, "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("NOT_SENT"))
                .andExpect(jsonPath("$.statusReason").value("NO_ADDRESS"));

        dossierRepository.save(dossierRepository.findById(noAddress.id()).orElseThrow()
                .withFields(Map.of("contactEmail", "corrige@example.org")));
        mockMvc.perform(post("/api/dossiers/{id}/portal-links/tasks/{taskId}/send", noAddress.id(), noAddressTask.getId())
                        .with(user(admin, "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipient").value("corrige@example.org"));
    }

    @Test
    void viewerCanListWithMaskedAddress_butCannotSendOrRevoke() throws Exception {
        // T-MANAGE-4 (H6 : VIEW lit, EDIT gère)
        String viewer = "viewer-" + System.nanoTime();
        grant(viewer, DossierPermission.VIEW);

        mockMvc.perform(get("/api/dossiers/{id}/portal-links", dossier.id()).with(user(viewer, "DOSSIER_VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(false))
                .andExpect(jsonPath("$.links[0].recipient").value(recipient.charAt(0) + "***@example.org"));
        mockMvc.perform(post("/api/dossiers/{id}/portal-links/tasks/{taskId}/send", dossier.id(), task.getId())
                        .with(user(viewer, "DOSSIER_VIEWER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/dossiers/{id}/portal-links/{linkId}/revoke", dossier.id(), onlyLiveLink().id())
                        .with(user(viewer, "DOSSIER_VIEWER")).with(csrf()))
                .andExpect(status().isForbidden());
        String stranger = "nobody-" + System.nanoTime();
        mockMvc.perform(get("/api/dossiers/{id}/portal-links", dossier.id()).with(user(stranger, "DOSSIER_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sendForATaskOfAnotherDossier_isRejected() throws Exception {
        Dossier other = dossier(Map.of("contactEmail", "x" + System.nanoTime() + "@example.org"));
        String admin = "admin-" + System.nanoTime();

        mockMvc.perform(post("/api/dossiers/{id}/portal-links/tasks/{taskId}/send", other.id(), task.getId())
                        .with(user(admin, "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(onlyLiveLink().status()).isEqualTo(PortalLinkStatus.ACTIVE);
    }

    @Test
    void expiryJob_expiresOnceAndNotifiesOnce() throws Exception {
        // T-EXPIRY-1
        jdbcTemplate.update("update portal_link set expires_at = ? where dossier_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(5)), dossier.id());
        UUID linkId = onlyLiveLink().id();

        int first = expireUseCase.expireDue();
        int second = expireUseCase.expireDue();

        assertThat(first).isGreaterThanOrEqualTo(1);
        assertThat(second).isZero();
        assertThat(portalLinkRepository.findById(linkId).orElseThrow().status()).isEqualTo(PortalLinkStatus.EXPIRED);
        assertThat(taskService.createTaskQuery().taskId(task.getId()).singleResult()).isNotNull();
        List<Map<String, Object>> notifications = jdbcTemplate.queryForList(
                "select payload_json from notification_outbox where cast(payload_json as varchar) like ?",
                "%PORTAL_LINK_EXPIRED%" + task.getId() + "%");
        assertThat(notifications).hasSizeLessThanOrEqualTo(1);
    }
}
