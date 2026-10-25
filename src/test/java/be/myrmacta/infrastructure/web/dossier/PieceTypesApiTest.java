package be.myrmacta.infrastructure.web.dossier;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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

/** TODO §105 (issue #211, lot P) — types de pièces : déclaration, dépôt classé, reclassement (PostgreSQL réel). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class PieceTypesApiTest {

    private static final String TENANT_ID = "tenant_dev";
    private static final String PIECE_TYPES = """
            [{"key": "preuveDomicile", "label": "Preuve de domicile", "sensitive": false},
             {"key": "identite", "label": "Pièce d'identité", "sensitive": true}]""";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pieceTypesAreDeclaredReturnedAndKeptWhenAPatchOmitsThem() throws Exception {
        String code = createType("PT_DECL");
        String admin = "admin-" + code;

        mockMvc.perform(get("/api/dossier-types/{code}", code).with(user(admin, "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pieceTypes[0].key").value("preuveDomicile"))
                .andExpect(jsonPath("$.pieceTypes[1].sensitive").value(true));

        patchType(code, "{\"label\": \"Renommé\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pieceTypes.length()").value(2));
        patchType(code, "{\"pieceTypes\": [{\"key\": \"preuveDomicile\", \"label\": \"Preuve\", \"sensitive\": false}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pieceTypes.length()").value(1))
                .andExpect(jsonPath("$.pieceTypes[0].label").value("Preuve"));
    }

    @Test
    void invalidPieceTypesAreRefused() throws Exception {
        String code = createType("PT_BAD");

        patchType(code, "{\"pieceTypes\": [{\"key\": \"Preuve-Domicile\", \"label\": \"X\", \"sensitive\": false}]}")
                .andExpect(status().isBadRequest());
        patchType(code, "{\"pieceTypes\": [{\"key\": \"a\", \"label\": \"A\"}, {\"key\": \"a\", \"label\": \"B\"}]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void attachmentIsClassifiedAtUploadThenReclassifiedAndUnclassified() throws Exception {
        String code = createType("PT_UPL");
        grant(code, "agent-" + code, "EDIT");
        String dossierId = createDossier(code, "agent-" + code);

        String elementId = idOf(upload(dossierId, "agent-" + code, "preuveDomicile")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metadata.pieceKey").value("preuveDomicile"))
                .andReturn());
        upload(dossierId, "agent-" + code, "inconnu").andExpect(status().isBadRequest());
        String unclassified = idOf(upload(dossierId, "agent-" + code, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metadata.pieceKey").doesNotExist())
                .andReturn());

        classify(dossierId, unclassified, "agent-" + code, "{\"pieceKey\": \"identite\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.pieceKey").value("identite"))
                .andExpect(jsonPath("$.metadata.pieceClassifiedBy").value("agent-" + code));
        classify(dossierId, elementId, "agent-" + code, "{\"pieceKey\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.pieceKey").doesNotExist());
        classify(dossierId, elementId, "agent-" + code, "{\"pieceKey\": \"inconnu\"}")
                .andExpect(status().isBadRequest());
        classify(dossierId, UUID.randomUUID().toString(), "agent-" + code, "{\"pieceKey\": \"identite\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void reclassifyingRequiresEdit() throws Exception {
        String code = createType("PT_ACL");
        grant(code, "agent-" + code, "EDIT");
        grant(code, "viewer-" + code, "VIEW");
        String dossierId = createDossier(code, "agent-" + code);
        String elementId = idOf(upload(dossierId, "agent-" + code, null).andExpect(status().isCreated()).andReturn());

        classify(dossierId, elementId, "viewer-" + code, "{\"pieceKey\": \"identite\"}")
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions upload(String dossierId, String userId, String pieceKey)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "piece.pdf", "application/pdf", "%PDF-content".getBytes(StandardCharsets.UTF_8));
        var request = multipart("/api/dossiers/{id}/elements/attachment", dossierId).file(file).param("label", "Pièce");
        if (pieceKey != null) {
            request.param("pieceKey", pieceKey);
        }
        return mockMvc.perform(request.with(user(userId)).with(csrf()));
    }

    private org.springframework.test.web.servlet.ResultActions classify(
            String dossierId, String elementId, String userId, String body) throws Exception {
        return mockMvc.perform(put("/api/dossiers/{id}/elements/{elementId}/piece-type", dossierId, elementId)
                .with(user(userId))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions patchType(String code, String body) throws Exception {
        return mockMvc.perform(patch("/api/dossier-types/{code}", code)
                .with(user("admin-" + code, "TENANT_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String createType(String prefix) throws Exception {
        String code = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
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
                                  "fieldDefinitions": [],
                                  "pieceTypes": %s
                                }
                                """.formatted(code, code, code.toLowerCase(), PIECE_TYPES)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pieceTypes.length()").value(2));
        return code;
    }

    private void grant(String code, String userId, String permission) throws Exception {
        mockMvc.perform(post("/api/dossier-types/{code}/acl", code)
                        .with(user("acl-" + code, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectUserId\": \"%s\", \"permission\": \"%s\"}".formatted(userId, permission)))
                .andExpect(status().isNoContent());
    }

    private String createDossier(String code, String userId) throws Exception {
        return idOf(mockMvc.perform(post("/api/dossiers")
                        .with(user(userId, "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dossierTypeCode\": \"%s\", \"label\": \"Dossier\", \"fields\": {}}".formatted(code)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private static String idOf(MvcResult result) throws Exception {
        var matcher = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) {
            throw new AssertionError("missing id");
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
