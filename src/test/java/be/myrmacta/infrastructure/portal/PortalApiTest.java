package be.myrmacta.infrastructure.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.dossier.port.out.AttachmentScanPort;
import be.myrmacta.domain.dossier.port.out.AttachmentScanResult;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatus;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatusReason;
import be.myrmacta.infrastructure.web.portal.FixedClockPortalRateLimiterConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** TODO §68 — API publique du portail, de bout en bout sur le vrai moteur (T-ACCESS, T-UPLOAD, T-SUBMIT, T-SEC). */
@AutoConfigureMockMvc
@Import(FixedClockPortalRateLimiterConfig.class)
class PortalApiTest extends AbstractPortalEngineTest {

    // Compteur et non System.nanoTime() : sous Windows nanoTime avance par pas de 100 ns, d'où
    // une poignée d'IP distinctes seulement — une IP « fraîche » retombait sur celle saturée par
    // le test de rate limit (429).
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    private static final String TOKEN = "X-Portal-Token";
    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AddDossierElementUseCase addDossierElementUseCase;

    @MockitoBean
    private AttachmentScanPort attachmentScanPort;

    @MockitoBean
    private DocumentStoragePort documentStoragePort;

    private UUID dossierId;
    private Task task;
    private String token;

    @BeforeEach
    void setUp() {
        ensureSchema();
        when(attachmentScanPort.scan(any(), anyString())).thenReturn(new AttachmentScanResult(true, null));
        when(documentStoragePort.store(anyString(), anyString(), any())).thenReturn("tenant_dev/portal.pdf");
        String recipient = "tiers-" + System.nanoTime() + "@example.org";
        dossierId = dossier(Map.of("contactEmail", recipient)).id();
        task = startPortalSmoke(dossierId);
        token = sendAndCaptureToken(recipient);
    }

    private static RequestPostProcessor ip(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }

    private static String freshIp() {
        int n = IP_SEQUENCE.getAndIncrement();
        return "10.20." + (n / 250 % 250) + "." + (1 + n % 250);
    }

    private PortalLink link() {
        return portalLinkRepository.findByDossierId(dossierId).getFirst();
    }

    @Test
    void validToken_returnsExactlyTheMinimalView() throws Exception {
        // T-ACCESS-1, D-PORTAL-DATA
        MvcResult result = mockMvc.perform(get("/api/portal/task").header(TOKEN, token).with(ip(freshIp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskName").value("Avis du tiers"))
                .andExpect(jsonPath("$.instructions").value(
                        "Merci de confirmer vos disponibilités et de joindre le devis signé."))
                .andExpect(jsonPath("$.form.fields[0].name").value("disponible"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> keys = new ArrayList<>(body.propertyNames());
        assertThat(keys).containsExactlyInAnyOrder(
                "dossierReference", "taskName", "instructions", "form", "expiresAt", "attachments");
        List<String> fieldKeys = new ArrayList<>(body.get("form").get("fields").get(0).propertyNames());
        assertThat(fieldKeys).doesNotContain("defaultValue", "sensitive");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("contactEmail").doesNotContain("@example.org");
        assertThat(link().lastAccessedAt()).isNotNull();
    }

    @Test
    void everyDenialCase_returnsTheSameBody() throws Exception {
        // T-ACCESS-2, T-ACCESS-3
        String unknown = mockMvc.perform(get("/api/portal/task").header(TOKEN, "x".repeat(43)).with(ip(freshIp())))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(get("/api/portal/task").with(ip(freshIp())))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String inQuery = mockMvc.perform(get("/api/portal/task").param("token", token).with(ip(freshIp())))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String overlong = mockMvc.perform(get("/api/portal/task").header(TOKEN, "y".repeat(500)).with(ip(freshIp())))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        PortalLink active = link();
        portalLinkRepository.transition(active, active.revoked(Instant.now(), "u1", PortalLinkStatusReason.REVOKED_BY_USER));
        String revoked = mockMvc.perform(get("/api/portal/task").header(TOKEN, token).with(ip(freshIp())))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(List.of(missing, inQuery, overlong, revoked)).allMatch(unknown::equals);
        assertThat(unknown).contains("Lien invalide ou expiré");
    }

    @Test
    void expiredLink_isRefusedEvenBeforeTheExpiryJobRuns() throws Exception {
        // T-ACCESS-2 (contrôle paresseux)
        jdbcTemplate.update("update portal_link set expires_at = ? where dossier_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), dossierId);

        mockMvc.perform(get("/api/portal/task").header(TOKEN, token).with(ip(freshIp())))
                .andExpect(status().isNotFound());
        assertThat(link().status()).isEqualTo(PortalLinkStatus.ACTIVE);
    }

    @Test
    void taskCompletedInternally_linkIsRefused() throws Exception {
        taskService.complete(task.getId());

        mockMvc.perform(get("/api/portal/task").header(TOKEN, token).with(ip(freshIp())))
                .andExpect(status().isNotFound());
    }

    @Test
    void linkIsReusableUntilSubmission() throws Exception {
        // T-ACCESS-6 (D-PORTAL-REUSE)
        String addr = freshIp();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/portal/task").header(TOKEN, token).with(ip(addr))).andExpect(status().isOk());
        }
    }

    @Test
    void rateLimitPerIp_returns429() throws Exception {
        // T-ACCESS-4 (30/min par défaut)
        String addr = "10.99.99.99";
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(get("/api/portal/task").header(TOKEN, "z".repeat(43)).with(ip(addr)))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/portal/task").header(TOKEN, token).with(ip(addr)))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * F-PORTAL-1 (revue Phase 9) — le paramètre {@code MultipartFile} est résolu, donc le corps lu
     * et parsé (jusqu'à 25 Mo, {@code resolve-lazily}), <em>avant</em> le corps de la méthode du
     * contrôleur : un anonyme pouvait faire ingérer un upload complet à chaque requête avant de
     * recevoir 404 ou 429. Une requête sans partie {@code file} révèle l'ordre : corps lu d'abord →
     * 400 ; refus d'abord → 404 neutre ou 429.
     */
    @Test
    void uploadWithUnusableTokenOrOverLimit_isRefusedBeforeTheMultipartBodyIsRead() throws Exception {
        String neutral = mockMvc.perform(get("/api/portal/task").header(TOKEN, "x".repeat(43)).with(ip(freshIp())))
                .andReturn().getResponse().getContentAsString();

        String unknown = mockMvc.perform(multipart("/api/portal/task/attachments")
                        .header(TOKEN, "x".repeat(43)).with(ip(freshIp())))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(multipart("/api/portal/task/attachments").with(ip(freshIp())))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(List.of(unknown, missing)).allMatch(neutral::equals);

        String addr = "10.98.98.98";
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(get("/api/portal/task").header(TOKEN, "z".repeat(43)).with(ip(addr)));
        }
        mockMvc.perform(multipart("/api/portal/task/attachments").header(TOKEN, token).with(ip(addr)))
                .andExpect(status().isTooManyRequests());

        // Jeton valide, sous la limite : la validation du corps reste celle d'avant.
        mockMvc.perform(multipart("/api/portal/task/attachments").header(TOKEN, token).with(ip(freshIp())))
                .andExpect(status().isBadRequest());
        assertThat(link().status()).isEqualTo(PortalLinkStatus.ACTIVE);
    }

    @Test
    void uploadedAttachment_isTracedToTheLink_andOnlyOwnAttachmentsAreListed() throws Exception {
        // T-UPLOAD-1, T-IDOR-1
        addDossierElementUseCase.add(new AddDossierElementUseCase.AddCommand(dossierId, ElementType.ATTACHMENT,
                "Pièce interne", "tenant_dev/internal.pdf", Map.of("filename", "interne.pdf"), "agent", null),
                AccessContext.system("test"));
        String addr = freshIp();

        mockMvc.perform(multipart("/api/portal/task/attachments")
                        .file(new MockMultipartFile("file", "devis.pdf", "application/pdf", PDF))
                        .param("label", "Devis signé")
                        .header(TOKEN, token)
                        .with(ip(addr)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Devis signé"))
                .andExpect(jsonPath("$.filename").value("devis.pdf"));

        mockMvc.perform(get("/api/portal/task").header(TOKEN, token).with(ip(addr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].label").value("Devis signé"));
        String addedBy = jdbcTemplate.queryForObject(
                "select added_by from dossier_element where dossier_id = ? and label = 'Devis signé'",
                String.class, dossierId);
        assertThat(addedBy).isEqualTo("portal:" + link().id());
    }

    @Test
    void uploadWithMismatchingContentOrForbiddenType_isRejected() throws Exception {
        // T-UPLOAD-2
        mockMvc.perform(multipart("/api/portal/task/attachments")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", "not a pdf".getBytes()))
                        .header(TOKEN, token).with(ip(freshIp())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/portal/task/attachments")
                        .file(new MockMultipartFile("file", "x.exe", "application/x-msdownload", "MZ".getBytes()))
                        .header(TOKEN, token).with(ip(freshIp())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadRejectedByAntivirus_isRejected() throws Exception {
        when(attachmentScanPort.scan(any(), anyString())).thenReturn(new AttachmentScanResult(false, "Eicar"));

        mockMvc.perform(multipart("/api/portal/task/attachments")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", PDF))
                        .header(TOKEN, token).with(ip(freshIp())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validSubmission_completesTheTaskOnce_andKillsTheLink() throws Exception {
        // T-SUBMIT-1
        String addr = freshIp();
        mockMvc.perform(post("/api/portal/task/submit").header(TOKEN, token).with(ip(addr))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"disponible\":true,\"commentaire\":\"Dispo lundi\"}}"))
                .andExpect(status().isNoContent());

        assertThat(taskService.createTaskQuery().taskId(task.getId()).singleResult()).isNull();
        assertThat(link().status()).isEqualTo(PortalLinkStatus.COMPLETED);
        Map<String, Object> answer = jdbcTemplate.queryForMap(
                "select submitted_by, schema_key from task_question_answer where task_id = ?", task.getId());
        assertThat(answer.get("SUBMITTED_BY")).isEqualTo("portal:" + link().id());
        mockMvc.perform(get("/api/portal/task").header(TOKEN, token).with(ip(addr))).andExpect(status().isNotFound());
    }

    @Test
    void invalidSubmission_isRejectedAndLinkStaysActive() throws Exception {
        // T-SUBMIT-2, T-SUBMIT-4
        mockMvc.perform(post("/api/portal/task/submit").header(TOKEN, token).with(ip(freshIp()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{\"commentaire\":\"x\"}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/portal/task/submit").header(TOKEN, token).with(ip(freshIp()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"disponible\":true,\"dossierId\":\"x\"}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/portal/task/submit").header(TOKEN, token).with(ip(freshIp()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"disponible\":true,\"inconnu\":\"x\"}}"))
                .andExpect(status().isBadRequest());

        assertThat(link().status()).isEqualTo(PortalLinkStatus.ACTIVE);
        assertThat(taskService.createTaskQuery().taskId(task.getId()).singleResult()).isNotNull();
    }

    @Test
    void concurrentSubmissions_completeTheTaskExactlyOnce() throws Exception {
        // T-SUBMIT-3 (vrais threads)
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String addr = "10.77.0." + (i + 1);
                calls.add(() -> mockMvc.perform(post("/api/portal/task/submit").header(TOKEN, token).with(ip(addr))
                                .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{\"disponible\":true}}"))
                        .andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Iterator<Future<Integer>> it = pool.invokeAll(calls).iterator(); it.hasNext(); ) {
                statuses.add(it.next().get());
            }
            assertThat(statuses).filteredOn(s -> s == 204).hasSize(1);
            assertThat(statuses).filteredOn(s -> s != 204).allMatch(s -> s == 404 || s == 409);
        } finally {
            pool.shutdownNow();
        }
        Integer answers = jdbcTemplate.queryForObject(
                "select count(*) from task_question_answer where task_id = ?", Integer.class, task.getId());
        assertThat(answers).isEqualTo(1);
    }

    @Test
    void portalResponses_carryTheApplicationSecurityHeaders() throws Exception {
        // T-SEC-2
        mockMvc.perform(get("/api/portal/task").header(TOKEN, token).with(ip(freshIp())))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("X-Frame-Options"))
                .andExpect(header().exists("Referrer-Policy"));
    }

    @Test
    void oversizedJsonBody_isRejectedBeforeBuffering() throws Exception {
        // T-SEC-4 (64 Ko par défaut)
        String huge = "{\"answers\":{\"commentaire\":\"" + "a".repeat(70_000) + "\"}}";
        mockMvc.perform(post("/api/portal/task/submit").header(TOKEN, token).with(ip(freshIp()))
                        .contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isPayloadTooLarge());
        assertThat(link().status()).isEqualTo(PortalLinkStatus.ACTIVE);
    }

    @Test
    void portalWithoutTrailingSlash_redirectsSoRelativeAssetsResolveUnderPortal() throws Exception {
        // Phase 7 : /portal (sans slash) servait index.html dont ./assets/… se résolvait vers
        // /assets/… (SPA interne, chaîne BFF) — page cassée. Le fragment #jeton est conservé par le
        // navigateur à travers une redirection sans fragment.
        mockMvc.perform(get("/portal").with(ip(freshIp())))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/portal/")));
    }

    @Test
    void unexpectedServerError_answersAGenericJson500_neverALoginRedirect() throws Exception {
        // Phase 7 : une erreur non prévue (panne de stockage) ne doit ni rediriger vers Keycloak
        // (révélant l'application interne) ni exposer de détail technique au tiers.
        when(documentStoragePort.store(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("disk /var/lib/myrmacta full at node-7"));

        MvcResult result = mockMvc.perform(multipart("/api/portal/task/attachments")
                        .file(new MockMultipartFile("file", "devis.pdf", "application/pdf", PDF))
                        .header(TOKEN, token).with(ip(freshIp())))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("node-7").doesNotContain("/var/lib");
        assertThat(result.getResponse().getHeader("Location")).isNull();
    }
}
