package be.myrmacta.infrastructure.web.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.support.OidcClientTestConfiguration;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class DossierElementContentApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentStoragePort documentStoragePort;

    @Autowired
    private AddDossierElementUseCase addDossierElementUseCase;

    @Autowired
    private DossierElementRepository dossierElementRepository;

    @Test
    void download_withViewAcl_returnsPdf() throws Exception {
        Fixture fixture = prepareProducedDoc("viewer-ok", true);
        grant(fixture.typeCode(), "viewer-ok", "VIEW");

        mockMvc.perform(get("/api/dossier-elements/{id}/content", fixture.elementId())
                        .with(user("viewer-ok", "DOSSIER_VIEWER")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes("%PDF-content".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void download_success_emitsElementContentRead_andNonDownloadableDoesNot() throws Exception {
        Fixture fixture = prepareProducedDoc("viewer-audit-ok", true);
        grant(fixture.typeCode(), "viewer-audit-ok", "VIEW");
        ListAppender<ILoggingEvent> appender = attachSecurityAuditAppender();
        try {
            mockMvc.perform(get("/api/dossier-elements/{id}/content", fixture.elementId())
                            .with(user("viewer-audit-ok", "DOSSIER_VIEWER")))
                    .andExpect(status().isOk());
        } finally {
            detachSecurityAuditAppender(appender);
        }
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.contains("event=element_content_read")
                        && msg.contains("elementId=" + fixture.elementId()));

        String code = createType("EL_note_audit");
        grant(code, "mgr-note-audit", "EDIT");
        String dossierId = createDossier(code, "mgr-note-audit");
        DossierElement note = dossierElementRepository.save(new DossierElement(
                UUID.randomUUID(),
                UUID.fromString(dossierId),
                TENANT_ID,
                ElementType.NOTE,
                "Note",
                null,
                Map.of(),
                "system",
                Instant.parse("2026-08-04T05:00:00Z"),
                null));
        appender = attachSecurityAuditAppender();
        try {
            mockMvc.perform(get("/api/dossier-elements/{id}/content", note.id())
                            .with(user("admin-note-audit", "TENANT_ADMIN")))
                    .andExpect(status().isConflict());
        } finally {
            detachSecurityAuditAppender(appender);
        }
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(msg -> msg.contains("event=element_content_read"));
    }

    @Test
    void download_withEditAcl_returns200() throws Exception {
        Fixture fixture = prepareProducedDoc("editor-ok", true);
        grant(fixture.typeCode(), "editor-ok", "EDIT");

        mockMvc.perform(get("/api/dossier-elements/{id}/content", fixture.elementId())
                        .with(user("editor-ok", "DOSSIER_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
    }

    @Test
    void download_withoutAcl_returns403() throws Exception {
        Fixture fixture = prepareProducedDoc("viewer-denied", true);
        mockMvc.perform(get("/api/dossier-elements/{id}/content", fixture.elementId())
                        .with(user("viewer-denied", "DOSSIER_VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void download_tenantAdminBypass_returns200() throws Exception {
        Fixture fixture = prepareProducedDoc("admin-bypass", true);
        mockMvc.perform(get("/api/dossier-elements/{id}/content", fixture.elementId())
                        .with(user("admin-bypass", "TENANT_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void download_workflowAdminBypass_returns200() throws Exception {
        Fixture fixture = prepareProducedDoc("wf-bypass", true);
        mockMvc.perform(get("/api/dossier-elements/{id}/content", fixture.elementId())
                        .with(user("wf-bypass", "WORKFLOW_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void download_unknownElement_returns404() throws Exception {
        mockMvc.perform(get("/api/dossier-elements/{id}/content", UUID.randomUUID())
                        .with(user("admin", "TENANT_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void download_brokenStorageRef_returns404() throws Exception {
        Fixture fixture = prepareProducedDoc("broken-ref", false);
        mockMvc.perform(get("/api/dossier-elements/{id}/content", fixture.elementId())
                        .with(user("admin-broken", "TENANT_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void download_nullStorageRef_returns409() throws Exception {
        String code = createType("EL_null_ref");
        grant(code, "mgr-null-ref", "EDIT");
        String dossierId = createDossier(code, "mgr-null-ref");
        DossierElement element = dossierElementRepository.save(new DossierElement(
                UUID.randomUUID(),
                UUID.fromString(dossierId),
                TENANT_ID,
                ElementType.PRODUCED_DOC,
                "Document sans storage",
                null,
                Map.of("templateKey", "contrat", "contentType", "application/pdf"),
                "system",
                Instant.parse("2026-08-04T05:00:00Z"),
                null));

        mockMvc.perform(get("/api/dossier-elements/{id}/content", element.id())
                        .with(user("admin-null-ref", "TENANT_ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void download_attachment_returnsOriginalContentTypeAndFilename() throws Exception {
        String code = createType("EL_ATT_OK");
        grant(code, "mgr-att-ok", "EDIT");
        String dossierId = createDossier(code, "mgr-att-ok");
        String ref = documentStoragePort.store(
                TENANT_ID, "image/png", "PNGDATA".getBytes(StandardCharsets.UTF_8));
        var element = addDossierElementUseCase.add(
                new AddDossierElementUseCase.AddCommand(
                        UUID.fromString(dossierId),
                        ElementType.ATTACHMENT,
                        "Justificatif",
                        ref,
                        Map.of("filename", "justificatif.png", "contentType", "image/png"),
                        "mgr-att-ok",
                        null),
                AccessContext.user("mgr-att-ok", java.util.Set.of("DOSSIER_MANAGER")));

        mockMvc.perform(get("/api/dossier-elements/{id}/content", element.id())
                        .with(user("mgr-att-ok", "DOSSIER_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("justificatif.png")))
                .andExpect(content().bytes("PNGDATA".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void download_informationNoteOrLink_hasNoDownloadableContent() throws Exception {
        String code = createType("EL_NODL");
        grant(code, "mgr-nodl", "EDIT");
        String dossierId = createDossier(code, "mgr-nodl");
        var element = addDossierElementUseCase.add(
                new AddDossierElementUseCase.AddCommand(
                        UUID.fromString(dossierId),
                        ElementType.NOTE,
                        "Note",
                        null,
                        Map.of("text", "x"),
                        "mgr-nodl",
                        null),
                AccessContext.user("mgr-nodl", java.util.Set.of("DOSSIER_MANAGER")));

        mockMvc.perform(get("/api/dossier-elements/{id}/content", element.id())
                        .with(user("mgr-nodl", "DOSSIER_MANAGER")))
                .andExpect(status().isConflict());
    }

    private Fixture prepareProducedDoc(String suffix, boolean storeBytes) throws Exception {
        String code = createType("EL_" + suffix);
        grant(code, "mgr-" + suffix, "EDIT");
        String dossierId = createDossier(code, "mgr-" + suffix);
        String ref = storeBytes
                ? documentStoragePort.store(
                        TENANT_ID, "application/pdf", "%PDF-content".getBytes(StandardCharsets.UTF_8))
                : TENANT_ID + "/" + UUID.randomUUID() + ".pdf";
        var element = addDossierElementUseCase.add(
                new AddDossierElementUseCase.AddCommand(
                        UUID.fromString(dossierId),
                        ElementType.PRODUCED_DOC,
                        "Document généré — test",
                        ref,
                        Map.of("templateKey", "contrat", "contentType", "application/pdf"),
                        "system",
                        "step1"),
                AccessContext.system("test"));
        return new Fixture(code, element.id());
    }

    private String createType(String prefix) throws Exception {
        String code = prefix.toUpperCase().replace('-', '_') + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-" + code, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "label": "Label %s",
                                  "workflowKey": "wf-%s",
                                  "active": true,
                                  "allowUnknownFields": true,
                                  "fieldDefinitions": []
                                }
                                """.formatted(code, code, code.toLowerCase())))
                .andExpect(status().isCreated());
        return code;
    }

    private void grant(String code, String userId, String permission) throws Exception {
        mockMvc.perform(post("/api/dossier-types/{code}/acl", code)
                        .with(user("acl-" + code, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectUserId": "%s",
                                  "permission": "%s"
                                }
                                """.formatted(userId, permission)))
                .andExpect(status().isNoContent());
    }

    private String createDossier(String code, String userId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/dossiers")
                        .with(user(userId, "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dossierTypeCode": "%s",
                                  "label": "Dossier %s",
                                  "fields": {}
                                }
                                """.formatted(code, code)))
                .andExpect(status().isCreated())
                .andReturn();
        var matcher = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) {
            throw new AssertionError("missing dossier id");
        }
        return matcher.group(1);
    }

    private static ListAppender<ILoggingEvent> attachSecurityAuditAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachSecurityAuditAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        logger.detachAppender(appender);
        appender.stop();
    }

    private static RequestPostProcessor user(String subject, String... roles) {
        String[] effective = roles.length == 0 ? new String[] {"DOSSIER_MANAGER"} : roles;
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT_ID))
                .authorities(java.util.Arrays.stream(effective)
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }

    private record Fixture(String typeCode, UUID elementId) {}
}
