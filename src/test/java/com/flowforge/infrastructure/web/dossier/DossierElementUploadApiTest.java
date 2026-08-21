package com.flowforge.infrastructure.web.dossier;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class DossierElementUploadApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void upload_validPdf_returns201WithMetadata() throws Exception {
        String code = createType("UPL_OK");
        grant(code, "mgr-upload-ok", "EDIT");
        String dossierId = createDossier(code, "mgr-upload-ok");
        MockMultipartFile file = new MockMultipartFile(
                "file", "justificatif.pdf", "application/pdf", "%PDF-content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/dossiers/{id}/elements/attachment", dossierId)
                        .file(file)
                        .param("label", "Justificatif")
                        .with(user("mgr-upload-ok"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ATTACHMENT"))
                .andExpect(jsonPath("$.downloadable").value(true))
                .andExpect(jsonPath("$.metadata.filename").value("justificatif.pdf"))
                .andExpect(jsonPath("$.metadata.contentType").value("application/pdf"));
    }

    @Test
    void upload_disallowedContentType_returns400() throws Exception {
        String code = createType("UPL_MIME");
        grant(code, "mgr-upload-mime", "EDIT");
        String dossierId = createDossier(code, "mgr-upload-mime");
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.sh", "application/x-sh", "echo hi".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/dossiers/{id}/elements/attachment", dossierId)
                        .file(file)
                        .param("label", "Script")
                        .with(user("mgr-upload-mime"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // Oversized-upload -> 413 is not exercisable through this MockMvc-based suite: the size limit
    // is enforced natively by the servlet container (Tomcat) at multipart-parsing time, and
    // MockMvcRequestBuilders.multipart(...).file(...) hands the dispatcher an already-parsed
    // MockMultipartHttpServletRequest that never goes through that container parsing step. The
    // DossierExceptionHandler -> 413 mapping itself is covered directly in
    // DossierExceptionHandlerTest#handleUploadTooLarge_mapsTo413.

    @Test
    void upload_contentDoesNotMatchDeclaredPdfType_returns400() throws Exception {
        String code = createType("UPL_SNIFF");
        grant(code, "mgr-upload-sniff", "EDIT");
        String dossierId = createDossier(code, "mgr-upload-sniff");
        // Declared application/pdf but the bytes are not a PDF signature (e.g. a renamed file).
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "not-a-pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/dossiers/{id}/elements/attachment", dossierId)
                        .file(file)
                        .param("label", "Fake")
                        .with(user("mgr-upload-sniff"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void upload_emptyFile_returns400() throws Exception {
        String code = createType("UPL_EMPTY");
        grant(code, "mgr-upload-empty", "EDIT");
        String dossierId = createDossier(code, "mgr-upload-empty");
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/dossiers/{id}/elements/attachment", dossierId)
                        .file(file)
                        .param("label", "Vide")
                        .with(user("mgr-upload-empty"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_viewerWithoutEditAcl_returns403() throws Exception {
        String code = createType("UPL_DENIED");
        grant(code, "viewer-upload-denied", "VIEW");
        grant(code, "creator-upload-denied", "EDIT");
        String dossierId = createDossier(code, "creator-upload-denied");
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/dossiers/{id}/elements/attachment", dossierId)
                        .file(file)
                        .param("label", "Denied")
                        .with(user("viewer-upload-denied"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    private String createType(String prefix) throws Exception {
        String code = prefix + "_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
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
}
