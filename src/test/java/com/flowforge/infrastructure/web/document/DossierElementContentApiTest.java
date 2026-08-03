package com.flowforge.infrastructure.web.document;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
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

    @Test
    void download_withViewAcl_returnsPdf() throws Exception {
        Fixture fixture = prepareProducedDoc("viewer-ok");
        grant(fixture.typeCode(), "viewer-ok", "VIEW");

        mockMvc.perform(get("/api/dossier-elements/{id}/content", fixture.elementId())
                        .with(user("viewer-ok", "DOSSIER_VIEWER")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes("%PDF-content".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void download_withoutAcl_returns403() throws Exception {
        Fixture fixture = prepareProducedDoc("viewer-denied");
        mockMvc.perform(get("/api/dossier-elements/{id}/content", fixture.elementId())
                        .with(user("viewer-denied", "DOSSIER_VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void download_tenantAdminBypass_returns200() throws Exception {
        Fixture fixture = prepareProducedDoc("admin-bypass");
        mockMvc.perform(get("/api/dossier-elements/{id}/content", fixture.elementId())
                        .with(user("admin-bypass", "TENANT_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void download_unknownElement_returns404() throws Exception {
        mockMvc.perform(get("/api/dossier-elements/{id}/content", UUID.randomUUID())
                        .with(user("admin", "TENANT_ADMIN")))
                .andExpect(status().isNotFound());
    }

    private Fixture prepareProducedDoc(String suffix) throws Exception {
        String code = createType("EL_" + suffix);
        grant(code, "mgr-" + suffix, "EDIT");
        String dossierId = createDossier(code, "mgr-" + suffix);
        String ref = documentStoragePort.store(
                TENANT_ID, "application/pdf", "%PDF-content".getBytes(StandardCharsets.UTF_8));
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
