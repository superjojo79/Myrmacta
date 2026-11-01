package be.myrmacta.infrastructure.web.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.connector.port.in.ManageConnectorInstanceUseCase;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.ConnectorDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.DossierTypeDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.TaskQuestionSchemaDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.WorkflowConfigDoc;
import be.myrmacta.domain.promotion.port.in.ImportConfigurationUseCase;
import be.myrmacta.domain.promotion.port.out.ArtifactCodec;
import be.myrmacta.domain.promotion.port.out.ConfigurationBundleCodec;
import be.myrmacta.domain.workflow.model.StatusOutcome;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.port.in.GetWorkflowTypeConfigUseCase;
import be.myrmacta.domain.workflow.port.in.ManageDraftsUseCase;
import be.myrmacta.domain.workflow.model.DraftArtifactType;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.domain.workflow.model.PublicationMode;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** TODO §83 (D4) — promotion de configuration à travers la vraie chaîne HTTP, Flowable et base H2. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class ConfigurationPromotionApiTest {

    private static final AccessContext.User ADMIN = AccessContext.user("alice", Set.of("TENANT_ADMIN"));
    private static final String SECRET = "super-secret-webhook-value-0123456789";

    @Autowired private MockMvc mockMvc;
    @Autowired private ArtifactCodec artifactCodec;
    @Autowired private ConfigurationBundleCodec bundleCodec;
    @Autowired private ImportConfigurationUseCase importUseCase;
    @Autowired private ManageDraftsUseCase manageDrafts;
    @Autowired private ManageConnectorInstanceUseCase manageConnector;
    @Autowired private DossierTypeRepository typeRepository;
    @Autowired private GetWorkflowTypeConfigUseCase workflowConfigs;
    @Autowired private be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase propose;
    @Autowired private be.myrmacta.domain.user.port.in.UserGroupsUseCase userGroups;
    @Autowired private be.myrmacta.domain.user.port.in.RecordUserLoginUseCase recordLogin;
    @Autowired private be.myrmacta.domain.mailroom.port.in.ManageReplyWorkflowUseCase replyWorkflows;
    @Autowired private be.myrmacta.domain.deadline.port.in.ManageDeadlineUseCase deadlines;

    private static RequestPostProcessor user(String id, String role) {
        return oidcLogin()
                .idToken(t -> t.subject(id).claim("tenant_id", "tenant_dev").claim("email", id + "@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static String bpmn(String key) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://myrmacta/test">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/><userTask id="wait" name="Wait"/><endEvent id="end"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="wait"/><sequenceFlow id="f2" sourceRef="wait" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(key);
    }

    private static String dmn(String key) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="def" name="Def" namespace="http://myrmacta/test">
                  <decision id="%s" name="Def">
                    <decisionTable id="dt" hitPolicy="UNIQUE">
                      <input><inputExpression id="ie" typeRef="string"><text>customerCat</text></inputExpression></input>
                      <output id="o" name="result" typeRef="string" />
                      <rule><inputEntry id="in"><text>"BRONZE"</text></inputEntry><outputEntry id="out"><text>'ok'</text></outputEntry></rule>
                    </decisionTable>
                  </decision>
                </definitions>
                """.formatted(key);
    }

    private BundleEntry json(ArtifactType type, String key, Object doc) {
        return new BundleEntry(type, key, artifactCodec.encode(doc));
    }

    /** Un bundle « d'un autre environnement » : rien de ce qu'il décrit n'existe encore ici. */
    private ConfigurationBundle freshBundle(String s, boolean withSecretField) {
        String typeCode = "PROMO_" + s.toUpperCase();
        String wfKey = "promoWf" + s;
        List<BundleEntry> entries = new ArrayList<>();
        entries.add(json(ArtifactType.DOSSIER_TYPE, typeCode, new DossierTypeDoc(typeCode, "Promo " + s, wfKey, true, false,
                List.of(new FieldDefinition("nom", "Nom", FieldType.STRING, false, null, null, null, false)), null, false, null,
                List.of())));
        entries.add(json(ArtifactType.TASK_QUESTION_SCHEMA, "promo_" + s, new TaskQuestionSchemaDoc("promo_" + s, "Question " + s,
                List.of(new FieldDefinition("q", "Q", FieldType.STRING, true, null, null, null, false)))));
        entries.add(json(ArtifactType.CONNECTOR_INSTANCE, "promo-conn-" + s, new ConnectorDoc("Promo Conn " + s.toUpperCase(), "http-rest", true,
                Map.of("webhookAuthType", "HMAC_SHA256", "processKey", "{{process_key}}", "dossierTypeCode", typeCode),
                withSecretField ? List.of("webhookSecret") : List.of())));
        entries.add(new BundleEntry(ArtifactType.DMN, "promoDmn" + s, dmn("promoDmn" + s).getBytes(StandardCharsets.UTF_8)));
        entries.add(new BundleEntry(ArtifactType.BPMN, wfKey, bpmn(wfKey).getBytes(StandardCharsets.UTF_8)));
        entries.add(json(ArtifactType.WORKFLOW_CONFIG, wfKey, new WorkflowConfigDoc(wfKey, "Promo flow " + s,
                List.of(new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE)), List.of(DossierStatus.OUVERT),
                null, List.of())));
        return new ConfigurationBundle(ConfigurationBundle.FORMAT_VERSION, Instant.parse("2026-10-01T10:00:00Z"), "recette", "test", entries);
    }

    private MockMultipartFile file(ConfigurationBundle bundle) {
        return new MockMultipartFile("file", "bundle.zip", "application/zip", bundleCodec.write(bundle));
    }

    private String planJson(ConfigurationBundle bundle, String parameters) throws Exception {
        var req = multipart("/api/admin/configuration/plan").file(file(bundle)).with(user("alice", "TENANT_ADMIN")).with(csrf());
        if (parameters != null) {
            req.param("parameters", parameters);
        }
        return mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void aFreshEnvironmentPlansCreatesThenAppliesAndReplayingConverges() throws Exception {
        String s = suffix();
        ConfigurationBundle bundle = freshBundle(s, true);

        // le paramètre {{process_key}} manque : le connecteur est BLOQUÉ, le reste se planifie
        mockMvc.perform(multipart("/api/admin/configuration/plan").file(file(bundle)).with(user("alice", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.type=='CONNECTOR_INSTANCE' && @.key=='promo-conn-%s')].action".formatted(s)).value("BLOCKED"))
                .andExpect(jsonPath("$.entries[?(@.type=='DOSSIER_TYPE')].action").value("CREATE"));

        String params = "{\"process_key\":\"pk-" + s + "\"}";
        String plan = planJson(bundle, params);
        // création de connecteur : le secret requis est demandé par son nom, jamais sa valeur
        assertThat(plan).contains("\"CREATE\"").contains("webhookSecret").doesNotContain(SECRET);

        // sans le secret, la création du connecteur reste BLOQUÉE ; avec, elle passe (désactivée)
        MvcResult noSecret = mockMvc.perform(multipart("/api/admin/configuration/apply").file(file(bundle))
                        .param("parameters", params).param("expectedBundleSha256", bundle.sha256())
                        .with(user("alice", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        assertThat(noSecret.getResponse().getContentAsString()).contains("SKIPPED");

        mockMvc.perform(multipart("/api/admin/configuration/apply").file(file(bundle))
                        .param("parameters", params)
                        .param("secrets", "{\"Promo Conn " + s.toUpperCase() + "\":{\"webhookSecret\":\"" + SECRET + "\"}}")
                        .param("expectedBundleSha256", bundle.sha256())
                        .with(user("alice", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.type=='CONNECTOR_INSTANCE' && @.key=='promo-conn-%s')].outcome".formatted(s)).value("APPLIED"))
                .andExpect(jsonPath("$.entries[?(@.type=='DOSSIER_TYPE')].outcome").value("UNCHANGED"));

        assertThat(typeRepository.findByCode("tenant_dev", "PROMO_" + s.toUpperCase())).isPresent();
        var created = manageConnector.list("tenant_dev", be.myrmacta.domain.shared.PageQuery.of(0, 100), ADMIN).content().stream()
                .filter(c -> c.name().equals("Promo Conn " + s.toUpperCase())).findFirst().orElseThrow();
        assertThat(created.enabled()).as("un connecteur importé est créé désactivé").isFalse();
        assertThat(created.config()).containsEntry("processKey", "pk-" + s);

        // BPMN et DMN : brouillons de provenance IMPORT, rien n'est déployé ni appliqué avant la publication humaine
        var pending = manageDrafts.listPending(ADMIN);
        var bpmnDraft = pending.stream().filter(d -> d.key().equals("promoWf" + s)).findFirst().orElseThrow();
        assertThat(bpmnDraft.provenance()).isEqualTo(Provenance.IMPORT);
        assertThat(bpmnDraft.hasProposedConfig()).isTrue();
        assertThat(pending).anyMatch(d -> d.key().equals("promoDmn" + s) && d.type() == DraftArtifactType.DMN);
        assertThat(workflowConfigs.getByKey("promoWf" + s)).isEmpty();

        // rejouer converge : plus rien d'applicable côté configuration directe, brouillons simplement réécrits
        String again = planJson(bundle, params);
        assertThat(again).contains("UNCHANGED");

        // après publication humaine, tout est « inchangé » — le cycle est bouclé
        manageDrafts.publish(ADMIN, DraftArtifactType.DMN, "promoDmn" + s,
                pending.stream().filter(d -> d.key().equals("promoDmn" + s)).findFirst().orElseThrow().updatedAt(), PublicationMode.MANUAL);
        manageDrafts.publish(ADMIN, DraftArtifactType.BPMN, "promoWf" + s, bpmnDraft.updatedAt(), PublicationMode.MANUAL);
        assertThat(workflowConfigs.getByKey("promoWf" + s)).isPresent();
        String finalPlan = planJson(bundle, params);
        var node = tools.jackson.databind.json.JsonMapper.builder().build().readTree(finalPlan);
        for (var entry : node.get("entries")) {
            assertThat(entry.get("action").asString()).as(entry.get("type").asString() + " " + entry.get("key").asString())
                    .isEqualTo("UNCHANGED");
        }

        mockMvc.perform(get("/api/admin/configuration/imports").with(user("alice", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bundleSha256").value(bundle.sha256()))
                .andExpect(jsonPath("$[0].channel").value("ADMIN"));
    }

    @Test
    void anExportNeverContainsASecretAndTheBundleReadsBackAsUnchanged() throws Exception {
        String s = suffix();
        manageConnector.create(new ManageConnectorInstanceUseCase.CreateCommand("tenant_dev", "Export conn " + s, "http-rest", false,
                Map.of("webhookAuthType", "HMAC_SHA256", "webhookSecret", SECRET, "processKey", "pk", "dossierTypeCode", "ANY")), ADMIN);

        MvcResult export = mockMvc.perform(get("/api/admin/configuration/export").param("sourceLabel", "recette")
                        .with(user("alice", "TENANT_ADMIN")))
                .andExpect(status().isOk()).andReturn();
        byte[] zip = export.getResponse().getContentAsByteArray();
        ConfigurationBundle bundle = bundleCodec.read(zip);
        assertThat(bundle.sourceLabel()).isEqualTo("recette");
        BundleEntry connector = bundle.of(ArtifactType.CONNECTOR_INSTANCE).stream()
                .filter(e -> e.text().contains("Export conn " + s)).findFirst().orElseThrow();
        assertThat(connector.text()).doesNotContain(SECRET).contains("webhookSecret"); // le nom, pas la valeur
        for (BundleEntry e : bundle.entries()) {
            assertThat(e.text()).as(e.toString()).doesNotContain(SECRET);
        }
        assertThat(export.getResponse().getHeader("X-Bundle-Sha256")).isEqualTo(bundle.sha256());
        assertThat(export.getResponse().getHeader("Content-Disposition")).contains("myrmacta-config-");

        // relu tel quel : le connecteur exporté est « inchangé »
        String plan = planJson(bundle, null);
        var node = tools.jackson.databind.json.JsonMapper.builder().build().readTree(plan);
        boolean found = false;
        for (var entry : node.get("entries")) {
            if (entry.get("key").asString().equals(connector.key())) {
                found = true;
                assertThat(entry.get("action").asString()).isEqualTo("UNCHANGED");
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void aBundleCarryingASecretIsRefusedAndNothingIsWritten() throws Exception {
        String s = suffix();
        ConfigurationBundle forged = new ConfigurationBundle(1, Instant.now(), "x", "", List.of(
                json(ArtifactType.CONNECTOR_INSTANCE, "forged-" + s, new ConnectorDoc("Forged " + s, "http-rest", true,
                        Map.of("webhookAuthType", "HMAC_SHA256", "webhookSecret", SECRET, "processKey", "p", "dossierTypeCode", "D"),
                        List.of()))));
        for (String endpoint : List.of("plan", "apply")) {
            mockMvc.perform(multipart("/api/admin/configuration/" + endpoint).file(file(forged)).with(user("alice", "TENANT_ADMIN")).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("never contains secrets")))
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(SECRET))));
        }
        assertThat(manageConnector.list("tenant_dev", be.myrmacta.domain.shared.PageQuery.of(0, 100), ADMIN).content())
                .noneMatch(c -> c.name().equals("Forged " + s));
    }

    @Test
    void anAlteredOrUnreadableArchiveIsRefused() throws Exception {
        byte[] zip = bundleCodec.write(freshBundle(suffix(), false));
        byte[] altered = zip.clone();
        // altère un octet du contenu d'un fichier : la somme du manifeste ne correspond plus
        altered[40] ^= 0x55;
        mockMvc.perform(multipart("/api/admin/configuration/plan").file(new MockMultipartFile("file", "b.zip", "application/zip", altered))
                        .with(user("alice", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/admin/configuration/plan")
                        .file(new MockMultipartFile("file", "b.zip", "application/zip", "not a zip".getBytes(StandardCharsets.UTF_8)))
                        .with(user("alice", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/admin/configuration/plan").file(new MockMultipartFile("file", "b.zip", "application/zip", new byte[0]))
                        .with(user("alice", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyingADifferentBundleThanThePlannedOneIsRefused() throws Exception {
        ConfigurationBundle bundle = freshBundle(suffix(), false);
        mockMvc.perform(multipart("/api/admin/configuration/apply").file(file(bundle))
                        .param("expectedBundleSha256", "0".repeat(64)).with(user("alice", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("plan it again")));
    }

    @Test
    void onlyATenantAdministratorMayExportPlanOrApply() throws Exception {
        mockMvc.perform(get("/api/admin/configuration/export").with(user("bob", "DOSSIER_VIEWER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/configuration/export").with(user("bob", "WORKFLOW_ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/configuration/plan").file(file(freshBundle(suffix(), false)))
                        .with(user("bob", "WORKFLOW_ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/configuration/imports").with(user("bob", "WORKFLOW_ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void theMcpChannelOnlyStagesBpmnAndDmnDraftsAndAcceptsNoSecret() {
        String s = suffix();
        ConfigurationBundle bundle = freshBundle(s, true);
        var agent = AccessContext.user("agent-1", Set.of("API_SERVICE"));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> importUseCase.apply(agent, bundle,
                Map.of("Promo Conn " + s.toUpperCase(), Map.of("webhookSecret", SECRET)), Map.of("process_key", "pk"), null,
                ImportConfigurationUseCase.Channel.MCP)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("never accepted through MCP");

        var report = importUseCase.apply(agent, bundle, Map.of(), Map.of("process_key", "pk"), bundle.sha256(),
                ImportConfigurationUseCase.Channel.MCP);

        assertThat(report.entries()).filteredOn(e -> e.type() == ArtifactType.DOSSIER_TYPE || e.type() == ArtifactType.TASK_QUESTION_SCHEMA)
                .allMatch(e -> e.outcome() == be.myrmacta.domain.promotion.model.ImportOutcome.SKIPPED && e.message().contains("admin import screen"));
        assertThat(report.entries()).filteredOn(e -> e.type() == ArtifactType.CONNECTOR_INSTANCE)
                .allMatch(e -> e.outcome() == be.myrmacta.domain.promotion.model.ImportOutcome.SKIPPED);
        assertThat(report.entries()).filteredOn(e -> e.type() == ArtifactType.BPMN || e.type() == ArtifactType.DMN)
                .allMatch(e -> e.outcome() == be.myrmacta.domain.promotion.model.ImportOutcome.APPLIED);
        assertThat(typeRepository.findByCode("tenant_dev", "PROMO_" + s.toUpperCase())).isEmpty();
        assertThat(manageDrafts.listPending(ADMIN)).anyMatch(d -> d.key().equals("promoWf" + s) && d.provenance() == Provenance.IMPORT);
    }

    /** TODO §117 (D-PROMO-GROUP) — un groupe voyage sans ses membres ; son groupe de l'IdP est un paramètre, conservé en mise à jour. */
    @Test
    void aGroupTravelsWithoutItsMembers_andItsIdentityProviderGroupIsAnEnvironmentParameter() throws Exception {
        String s = suffix().toUpperCase();
        String code = "PROMO_GRP_" + s;
        recordLogin.record("member-" + s, "Membre", "m@example.com", Set.of("DOSSIER_VIEWER"));
        userGroups.create(code, "Groupe " + s, "sg-recette-" + s);
        userGroups.addMember(code, "member-" + s);

        byte[] zip = mockMvc.perform(get("/api/admin/configuration/export").with(user("alice", "TENANT_ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        ConfigurationBundle exported = bundleCodec.read(zip);
        assertThat(exported.formatVersion()).isEqualTo(2);
        BundleEntry entry = exported.find(ArtifactType.USER_GROUP, code).orElseThrow();
        assertThat(entry.text()).contains("Groupe " + s).contains("sg-recette-" + s).doesNotContain("member-" + s);

        // sur « un autre environnement » : le groupe n'existe pas, son groupe de l'IdP vient d'un paramètre
        String target = "PROMO_NEW_" + s;
        ConfigurationBundle bundle = new ConfigurationBundle(ConfigurationBundle.FORMAT_VERSION, Instant.parse("2026-11-01T10:00:00Z"),
                "recette", "test", List.of(json(ArtifactType.USER_GROUP, target,
                        new be.myrmacta.domain.promotion.model.doc.ConfigDocs.UserGroupDoc(target, "Nouveau " + s, "{{idp_groupe}}"))));
        mockMvc.perform(multipart("/api/admin/configuration/plan").file(file(bundle)).with(user("alice", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].action").value("BLOCKED"));
        mockMvc.perform(multipart("/api/admin/configuration/apply").file(file(bundle))
                        .param("parameters", "{\"idp_groupe\":\"sg-prod-" + s + "\"}")
                        .param("expectedBundleSha256", bundle.sha256())
                        .with(user("alice", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].outcome").value("APPLIED"));
        assertThat(userGroups.get(target).idpGroup()).isEqualTo("sg-prod-" + s);
        assertThat(userGroups.get(target).memberIds()).isEmpty();

        // rejouer avec un autre groupe de l'IdP ne remplace pas celui de la cible : « inchangé », avec la différence signalée
        String replay = planJson(bundle, "{\"idp_groupe\":\"sg-autre\"}");
        assertThat(replay).contains("UNCHANGED").contains("sg-prod-" + s);
    }

    /**
     * TODO §117 (D-PROMO-TYPE-SETTINGS) — un seul passage crée le type, sa configuration de workflow (sans BPMN, appliquée
     * directement), le groupe d'escalade, puis le workflow « réponse » et l'échéance qui en dépendent ; l'export les restitue et
     * rejouer converge.
     */
    @Test
    void replyWorkflowAndDeadlineTravelWithTheTypeTheyDependOn() throws Exception {
        String s = suffix().toUpperCase();
        String typeCode = "PROMO_RW_" + s;
        String wfKey = "promoReply" + s.toLowerCase();
        String groupCode = "PROMO_ESC_" + s;
        ConfigurationBundle bundle = new ConfigurationBundle(ConfigurationBundle.FORMAT_VERSION, Instant.parse("2026-11-01T10:00:00Z"),
                "recette", "test", List.of(
                        json(ArtifactType.USER_GROUP, groupCode,
                                new be.myrmacta.domain.promotion.model.doc.ConfigDocs.UserGroupDoc(groupCode, "Escalade", null)),
                        json(ArtifactType.DOSSIER_TYPE, typeCode, new DossierTypeDoc(typeCode, "Type " + s, wfKey, true, false,
                                List.of(new FieldDefinition("terme", "Terme", FieldType.DATE, false, null, null, null, false)), null, false,
                                null, List.of())),
                        json(ArtifactType.WORKFLOW_CONFIG, wfKey, new WorkflowConfigDoc(wfKey, "Réponse " + s,
                                List.of(new WorkflowStatusDefinition("RECU", "Reçu", true, StatusOutcome.NONE)),
                                List.of(DossierStatus.OUVERT), null, List.of())),
                        json(ArtifactType.REPLY_WORKFLOW, typeCode, new be.myrmacta.domain.promotion.model.doc.ConfigDocs.ReplyWorkflowDoc(
                                typeCode, be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting.Mode.SUR_DOSSIER, wfKey, null)),
                        json(ArtifactType.DEADLINE, typeCode,
                                new be.myrmacta.domain.promotion.model.doc.ConfigDocs.DeadlineDoc(typeCode, "terme", 3, groupCode))));

        String plan = planJson(bundle, null);
        assertThat(plan).doesNotContain("BLOCKED").doesNotContain("CONFLICT");

        mockMvc.perform(multipart("/api/admin/configuration/apply").file(file(bundle)).param("expectedBundleSha256", bundle.sha256())
                        .with(user("alice", "TENANT_ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.type=='REPLY_WORKFLOW')].outcome").value("APPLIED"))
                .andExpect(jsonPath("$.entries[?(@.type=='DEADLINE')].outcome").value("APPLIED"));
        assertThat(replyWorkflows.get(typeCode)).hasValueSatisfying(r -> assertThat(r.workflowKey()).isEqualTo(wfKey));
        assertThat(deadlines.get(typeCode)).hasValueSatisfying(d -> {
            assertThat(d.fieldName()).isEqualTo("terme");
            assertThat(d.reminderDays()).isEqualTo(3);
            assertThat(d.escalationGroup()).isEqualTo(groupCode);
        });

        // l'export les restitue, à l'identique : rejouer converge
        ConfigurationBundle exported = bundleCodec.read(mockMvc.perform(get("/api/admin/configuration/export")
                .with(user("alice", "TENANT_ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(exported.find(ArtifactType.REPLY_WORKFLOW, typeCode)).isPresent();
        assertThat(exported.find(ArtifactType.DEADLINE, typeCode)).isPresent();
        var node = tools.jackson.databind.json.JsonMapper.builder().build().readTree(planJson(bundle, null));
        for (var entry : node.get("entries")) {
            assertThat(entry.get("action").asString()).as(entry.get("type").asString() + " " + entry.get("key").asString())
                    .isEqualTo("UNCHANGED");
        }

        // un réglage retiré de la cible revient au prochain import
        deadlines.clear(typeCode);
        assertThat(planJson(bundle, null)).contains("\"type\":\"DEADLINE\"").contains("\"CREATE\"");
    }
}
