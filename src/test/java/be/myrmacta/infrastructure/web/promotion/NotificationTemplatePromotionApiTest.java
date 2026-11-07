package be.myrmacta.infrastructure.web.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.port.in.ActivateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import be.myrmacta.domain.promotion.artifact.NotificationTemplateArtifacts;
import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;
import be.myrmacta.domain.promotion.model.ImportOutcome;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.NotificationTemplateDoc;
import be.myrmacta.domain.promotion.port.in.ImportConfigurationUseCase;
import be.myrmacta.domain.promotion.port.out.ArtifactCodec;
import be.myrmacta.domain.promotion.port.out.ConfigurationBundleCodec;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * TODO §123 (H1, CS10) — la version active d'un gabarit de notification voyage dans un bundle, par la vraie
 * chaîne HTTP, Flowable et la base ; l'import crée un brouillon, jamais activé ; rejouer converge ; le canal MCP dépose aussi ce
 * brouillon.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class NotificationTemplatePromotionApiTest {

    private static final String TENANT = "tenant_dev";

    @Autowired private MockMvc mockMvc;
    @Autowired private ArtifactCodec artifactCodec;
    @Autowired private ConfigurationBundleCodec bundleCodec;
    @Autowired private ImportConfigurationUseCase importUseCase;
    @Autowired private CreateNotificationTemplateUseCase createTemplate;
    @Autowired private ActivateNotificationTemplateUseCase activateTemplate;
    @Autowired private NotificationTemplateRepository templateRepository;

    private static RequestPostProcessor user(String id, String role) {
        return oidcLogin()
                .idToken(t -> t.subject(id).claim("tenant_id", TENANT).claim("email", id + "@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private BundleEntry entry(NotificationTemplateDoc doc) {
        return new BundleEntry(ArtifactType.NOTIFICATION_TEMPLATE, NotificationTemplateArtifacts.entryKey(doc), artifactCodec.encode(doc));
    }

    private ConfigurationBundle bundleOf(BundleEntry... entries) {
        return new ConfigurationBundle(
                ConfigurationBundle.FORMAT_VERSION, Instant.parse("2026-11-01T10:00:00Z"), "recette", "test", List.of(entries));
    }

    private MockMultipartFile file(ConfigurationBundle bundle) {
        return new MockMultipartFile("file", "bundle.zip", "application/zip", bundleCodec.write(bundle));
    }

    private List<NotificationTemplate> versions(String key) {
        return templateRepository.pageByTenantId(TENANT, key, PageQuery.of(0, 100)).content();
    }

    @Test
    void theActiveNotificationTemplateTravels_andIsImportedAsADraftThatAHumanActivates() throws Exception {
        String key = "promo-notif-" + UUID.randomUUID().toString().substring(0, 8);
        NotificationTemplate v1 = createTemplate.create(new CreateNotificationTemplateUseCase.CreateCommand(
                key, null, "fr", "Accusé", "Dossier [[${dossier.reference}]]",
                "Bonjour, votre dossier est enregistré.", "<p th:text=\"${dossier.reference}\">ref</p>", "alice"));
        activateTemplate.activate(v1.id(), "alice");
        createTemplate.create(new CreateNotificationTemplateUseCase.CreateCommand(
                key, null, "fr", "Accusé", "Brouillon local", "Texte du brouillon local", null, "alice"));

        // export : la version active seule, avec ses trois parties
        ConfigurationBundle exported = bundleCodec.read(mockMvc.perform(get("/api/admin/configuration/export")
                        .with(user("alice", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        BundleEntry exportedEntry = exported.find(ArtifactType.NOTIFICATION_TEMPLATE, key + "._.fr").orElseThrow();
        assertThat(exportedEntry.text())
                .contains("Bonjour, votre dossier est enregistré.")
                .contains("dossier.reference")
                .doesNotContain("Brouillon local");

        // rejouer l'export tel quel : rien à faire (la version active est identique)
        assertThat(plan(bundleOf(exportedEntry))).contains("UNCHANGED");

        // la recette a fait évoluer le gabarit : sur la cible, une nouvelle version DRAFT, l'active est inchangée
        NotificationTemplateDoc evolved = new NotificationTemplateDoc(
                key, null, "fr", "Accusé", "Nouvel objet", "Nouveau texte", "<p>Nouveau</p>");
        ConfigurationBundle bundle = bundleOf(entry(evolved));
        assertThat(plan(bundle)).contains("\"UPDATE\"").contains("activate it");
        mockMvc.perform(multipart("/api/admin/configuration/apply")
                        .file(file(bundle))
                        .param("expectedBundleSha256", bundle.sha256())
                        .with(user("alice", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].outcome").value("APPLIED"));
        assertThat(versions(key)).filteredOn(t -> t.status() == TemplateStatus.ACTIVE).singleElement()
                .satisfies(t -> assertThat(t.textBody()).isEqualTo("Bonjour, votre dossier est enregistré."));
        assertThat(versions(key)).anyMatch(t -> t.status() == TemplateStatus.DRAFT
                && t.subject().equals("Nouvel objet")
                && t.textBody().equals("Nouveau texte")
                && "<p>Nouveau</p>".equals(t.htmlBody()));

        // rejouer converge : le brouillon identique suffit
        assertThat(plan(bundle)).contains("UNCHANGED").contains("draft version");
    }

    @Test
    void aTemplateRefusedByThePolicyIsBlockedAtThePlan() throws Exception {
        String key = "promo-notif-" + UUID.randomUUID().toString().substring(0, 8);
        NotificationTemplateDoc image = new NotificationTemplateDoc(
                key, null, null, "Image", "Objet", "Texte", "<p><img src=\"https://example.com/x.png\"></p>");

        String plan = plan(bundleOf(entry(image)));

        assertThat(plan).contains("\"BLOCKED\"").contains("refused at activation");
    }

    @Test
    void theMcpChannelStagesNotificationTemplateDrafts() {
        String key = "promo-notif-" + UUID.randomUUID().toString().substring(0, 8);
        NotificationTemplateDoc doc = new NotificationTemplateDoc(key, "court", null, "Court", "Objet", "Texte", null);
        ConfigurationBundle bundle = bundleOf(entry(doc));

        var report = importUseCase.apply(AccessContext.user("agent-1", Set.of("API_SERVICE")), bundle, Map.of(), Map.of(),
                bundle.sha256(), ImportConfigurationUseCase.Channel.MCP);

        assertThat(report.entries().getFirst().outcome()).isEqualTo(ImportOutcome.APPLIED);
        assertThat(versions(key)).singleElement().satisfies(t -> {
            assertThat(t.status()).isEqualTo(TemplateStatus.DRAFT);
            assertThat(t.variantKey()).isEqualTo("court");
            assertThat(t.updatedBy()).isEqualTo("agent-1");
        });
    }

    private String plan(ConfigurationBundle bundle) throws Exception {
        return mockMvc.perform(multipart("/api/admin/configuration/plan")
                        .file(file(bundle))
                        .with(user("alice", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
