package be.myrmacta.infrastructure.web.notification.template;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import tools.jackson.databind.json.JsonMapper;

/** API REST des gabarits de notification, sur la vraie chaîne HTTP et sécurité (CS1, CS6, CS9 au niveau API). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class NotificationTemplateApiTest {

    private static final String TENANT_ID = "tenant_dev";
    private static final String BASE = "/api/notification-templates";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    private static String key(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    @Test
    void createGetListActivateResolve_happyPath() throws Exception {
        String key = key("api");
        String id = createDraft(key, "premium", "fr", "Accusé : [[${dossier.reference}]]", "Dossier [[${dossier.reference}]]",
                "<p th:text=\"${dossier.reference}\">x</p>");

        mockMvc.perform(get(BASE + "/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.subject").value("Accusé : [[${dossier.reference}]]"))
                .andExpect(jsonPath("$.textBody").value("Dossier [[${dossier.reference}]]"))
                .andExpect(jsonPath("$.htmlBody").value(containsString("th:text")));

        mockMvc.perform(get(BASE).param("templateKey", key).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].templateKey").value(key))
                .andExpect(jsonPath("$.content[0].subject").value("Accusé : [[${dossier.reference}]]"))
                .andExpect(jsonPath("$.content[0].textBody").value(""))
                .andExpect(jsonPath("$.content[0].htmlBody").value(nullValue()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(post(BASE + "/" + id + "/activate").with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get(BASE + "/resolve")
                        .param("templateKey", key)
                        .param("variantKey", "premium")
                        .param("locale", "fr")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void resolve_followsTheFourLevelCascade_andMissingIs404() throws Exception {
        String key = key("cascade");
        String def = createAndActivate(key, null, null, "defaut");
        String fr = createAndActivate(key, null, "fr", "fr");
        String premium = createAndActivate(key, "premium", null, "premium");
        String premiumFr = createAndActivate(key, "premium", "fr", "premium-fr");

        assertResolves(key, "premium", "fr", premiumFr);
        assertResolves(key, "premium", "nl", premium);
        assertResolves(key, "gold", "fr", fr);
        assertResolves(key, "gold", "nl", def);
        assertResolves(key, null, null, def);
        mockMvc.perform(get(BASE + "/resolve").param("templateKey", "Bad Key").with(admin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/resolve").param("templateKey", key("absent")).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_patchesADraft_andRefusesAnActiveOne() throws Exception {
        String id = createDraft(key("upd"), null, null, "S", "T", null);

        mockMvc.perform(put(BASE + "/" + id)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Nouveau\",\"subject\":\"S2\",\"htmlBody\":\"<p>h</p>\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Nouveau"))
                .andExpect(jsonPath("$.subject").value("S2"))
                .andExpect(jsonPath("$.textBody").value("T"))
                .andExpect(jsonPath("$.htmlBody").value("<p>h</p>"));

        mockMvc.perform(post(BASE + "/" + id + "/activate").with(admin()).with(csrf())).andExpect(status().isOk());

        mockMvc.perform(put(BASE + "/" + id)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"S3\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put(BASE + "/" + UUID.randomUUID())
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"S3\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void lifecycle_newVersionArchiveAndConflicts() throws Exception {
        String id = createDraft(key("life"), null, null, "S", "T", null);
        mockMvc.perform(post(BASE + "/" + id + "/archive").with(admin()).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post(BASE + "/" + id + "/activate").with(admin()).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/" + id + "/activate").with(admin()).with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(post(BASE + "/" + id + "/new-version").with(admin()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.subject").value("S"));

        mockMvc.perform(post(BASE + "/" + id + "/archive").with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(get(BASE + "/" + UUID.randomUUID()).with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/activate").with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_rejectsInvalidFields_with400() throws Exception {
        for (String payload : List.of(
                "{\"templateKey\":\" \",\"subject\":\"S\",\"textBody\":\"T\"}",
                "{\"templateKey\":\"Bad Key\",\"subject\":\"S\",\"textBody\":\"T\"}",
                "{\"templateKey\":\"ok\",\"subject\":\" \",\"textBody\":\"T\"}",
                "{\"templateKey\":\"ok\",\"subject\":\"S\"}",
                "{\"templateKey\":\"ok\",\"variantKey\":\"BAD\",\"subject\":\"S\",\"textBody\":\"T\"}",
                "{\"templateKey\":\"ok\",\"locale\":\"fr_BE\",\"subject\":\"S\",\"textBody\":\"T\"}",
                "{\"templateKey\":\"ok\",\"subject\":\"" + "s".repeat(501) + "\",\"textBody\":\"T\"}",
                "not json")) {
            mockMvc.perform(post(BASE)
                            .with(admin())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void activate_refusesAHtmlOutsideThePolicy_namingThePartAndTheRule() throws Exception {
        record Case(String html, String expected) {}
        for (Case c : List.of(
                new Case("<p>x</p><img src=\"https://t.example/p.gif\">", "<img>"),
                new Case("<p onclick=\"x()\">x</p>", "onclick"),
                new Case("<style>p{}</style><p>x</p>", "<style>"),
                new Case("<p style=\"background:url(x)\">x</p>", "style"),
                new Case("<a th:href=\"${dossier.label}\">x</a>", "th:href"),
                new Case("<a href=\"javascript:alert(1)\">x</a>", "href"),
                new Case("<p th:utext=\"${dossier.label}\">x</p>", "Unescaped"))) {
            String id = createDraft(key("policy"), null, null, "S", "T", c.html());

            mockMvc.perform(post(BASE + "/" + id + "/activate").with(admin()).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("htmlBody")))
                    .andExpect(jsonPath("$.detail").value(containsString(c.expected())));

            mockMvc.perform(get(BASE + "/" + id).with(admin())).andExpect(jsonPath("$.status").value("DRAFT"));
        }
    }

    @Test
    void activate_refusesAnExpressionOutsideTheGrammar_inEachPart() throws Exception {
        String evil = "[[${T(java.lang.Runtime).getRuntime()}]]";
        mockMvc.perform(post(BASE + "/" + createDraft(key("ssti"), null, null, evil, "T", null) + "/activate")
                        .with(admin()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("subject")));
        mockMvc.perform(post(BASE + "/" + createDraft(key("ssti"), null, null, "S", evil, null) + "/activate")
                        .with(admin()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("textBody")));
        mockMvc.perform(post(BASE + "/" + createDraft(key("ssti"), null, null, "S", "T",
                                "<p th:text=\"${T(java.lang.Runtime).getRuntime()}\">x</p>") + "/activate")
                        .with(admin()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("htmlBody")));
    }

    @Test
    void preview_byContent_rendersAllThreeParts() throws Exception {
        mockMvc.perform(post(BASE + "/preview")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of(
                                "template", Map.of(
                                        "subject", "Accusé : [[${dossier.fields.objet}]]",
                                        "textBody", "Dossier [[${dossier.reference}]]",
                                        "htmlBody", "<p th:text=\"${dossier.reference}\">x</p>"),
                                "model", Map.of("dossier", Map.of("reference", "DOS-1", "fields", Map.of("objet", "l'été")))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Accusé : l'été"))
                .andExpect(jsonPath("$.textBody").value("Dossier DOS-1"))
                .andExpect(jsonPath("$.htmlBody").value(containsString("<p>DOS-1</p>")));
    }

    @Test
    void preview_byId_rendersADraft_andIsStrictOnAMissingVariable() throws Exception {
        String id = createDraft(key("prev"), null, null, "S", "Montant [[${variables.montant}]]", null);

        mockMvc.perform(post(BASE + "/preview")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + id + "\",\"model\":{\"variables\":{\"montant\":12}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textBody").value("Montant 12"))
                .andExpect(jsonPath("$.htmlBody").value(nullValue()));

        mockMvc.perform(post(BASE + "/preview")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + id + "\",\"model\":{\"variables\":{\"autre\":\"SECRET-VALUE\"}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("variables.montant")))
                .andExpect(jsonPath("$.detail").value(not(containsString("SECRET-VALUE"))));
    }

    @Test
    void preview_refusesAnInvalidTemplate_aBadRequest_andAnOversizedModel() throws Exception {
        mockMvc.perform(post(BASE + "/preview")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template\":{\"subject\":\"S\",\"textBody\":\"T\",\"htmlBody\":\"<img src=x>\"},\"model\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("<img>")));
        mockMvc.perform(post(BASE + "/preview")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":{}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/preview")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template\":{\"subject\":\"S\",\"textBody\":\"T\"},\"model\":{\"variables\":{\"big\":\""
                                + "x".repeat(70_000) + "\"}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("model")));
        mockMvc.perform(post(BASE + "/preview")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void preview_aRequestBeyondTheCeiling_isRejectedBeforeBeingRead() throws Exception {
        mockMvc.perform(post(BASE + "/preview")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template\":{\"subject\":\"S\",\"textBody\":\"" + "x".repeat(1_000_000) + "\"}}"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void onlyTenantAdmin_mayUseTheApi() throws Exception {
        String id = createDraft(key("sec"), null, null, "S", "T", null);
        String body = "{\"templateKey\":\"sec-denied\",\"subject\":\"S\",\"textBody\":\"T\"}";

        for (RequestPostProcessor denied : List.of(
                user("manager", "DOSSIER_MANAGER"),
                user("wf", "WORKFLOW_ADMIN"),
                user("viewer", "DOSSIER_VIEWER"),
                user("svc", "API_SERVICE"))) {
            mockMvc.perform(get(BASE).with(denied)).andExpect(status().isForbidden());
            mockMvc.perform(get(BASE + "/" + id).with(denied)).andExpect(status().isForbidden());
            mockMvc.perform(get(BASE + "/resolve").param("templateKey", "x").with(denied))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(BASE).with(denied).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put(BASE + "/" + id).with(denied).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(BASE + "/" + id + "/activate").with(denied).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(BASE + "/" + id + "/archive").with(denied).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(BASE + "/" + id + "/new-version").with(denied).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(BASE + "/preview").with(denied).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"" + id + "\"}"))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE).param("size", "101").with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE).param("sort", "label,asc").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void theApiNeverReturnsADraftBody_toTheResolveEndpointUntilActive() throws Exception {
        String key = key("hidden");
        createDraft(key, null, null, "S", "T", null);

        mockMvc.perform(get(BASE + "/resolve").param("templateKey", key).with(admin()))
                .andExpect(status().isNotFound());
    }

    private void assertResolves(String key, String variant, String locale, String expectedId) throws Exception {
        var request = get(BASE + "/resolve").param("templateKey", key).with(admin());
        if (variant != null) {
            request = request.param("variantKey", variant);
        }
        if (locale != null) {
            request = request.param("locale", locale);
        }
        mockMvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(expectedId));
    }

    private String createAndActivate(String key, String variant, String locale, String subject) throws Exception {
        String id = createDraft(key, variant, locale, subject, "Corps " + subject, null);
        mockMvc.perform(post(BASE + "/" + id + "/activate").with(admin()).with(csrf())).andExpect(status().isOk());
        return id;
    }

    private String createDraft(String key, String variant, String locale, String subject, String textBody, String htmlBody)
            throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("templateKey", key);
        payload.put("variantKey", variant);
        payload.put("locale", locale);
        payload.put("label", "label");
        payload.put("subject", subject);
        payload.put("textBody", textBody);
        payload.put("htmlBody", htmlBody);
        MvcResult created = mockMvc.perform(post(BASE)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return JSON.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }

    private static RequestPostProcessor admin() {
        return user("notification-template-admin", "TENANT_ADMIN");
    }

    private static RequestPostProcessor user(String subject, String... roles) {
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT_ID))
                .authorities(java.util.Arrays.stream(roles)
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
