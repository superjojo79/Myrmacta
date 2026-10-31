package be.myrmacta.infrastructure.web.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.port.out.KnownUserRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §113 (issue #190, D-GROUP-ASSIGN) — « Tâches » par groupe, par la vraie chaîne HTTP et sécurité : un membre listé et un
 * membre venu de la claim de l'IdP voient la tâche libre du groupe et peuvent la prendre ; un non-membre ne la voit pas ; une
 * fois prise, elle sort de la liste des autres membres.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class GroupTasksApiTest {

    private static final String TENANT = "tenant_dev";
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Autowired private MockMvc mockMvc;
    @Autowired private KnownUserRepository knownUsers;

    @Test
    void theFreeTaskOfAGroupIsSeenByItsMembersOnly_untilOneOfThemTakesIt() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String group = "COMPTA_" + suffix;
        String type = "FACTURE_" + suffix;
        String process = "traiterFacture" + suffix;
        String alice = "alice-" + suffix;
        String bob = "bob-" + suffix;
        String carol = "carol-" + suffix;
        known(alice, Set.of());
        known(bob, Set.of("/services/compta-" + suffix));
        known(carol, Set.of("/services/rh"));
        configure(type, process, group, List.of(alice, bob, carol));
        mockMvc.perform(post("/api/user-groups").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + group + "\",\"label\":\"Comptabilité\",\"idpGroup\":\"/services/compta-" + suffix + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/user-groups/{code}/members/{userId}", group, alice).with(admin()).with(csrf()))
                .andExpect(status().isOk());

        String dossierId = startWorkflow(type, process, alice);

        JsonNode aliceTask = taskOn(alice, dossierId);
        assertThat(aliceTask).isNotNull();
        assertThat(aliceTask.get("assigneeId").isNull()).isTrue();
        assertThat(taskOn(bob, dossierId)).isNotNull();
        assertThat(taskOn(carol, dossierId)).isNull();

        mockMvc.perform(post("/api/workflow-links/{linkId}/tasks/{taskId}/claim",
                        aliceTask.get("linkId").asString(), aliceTask.get("taskId").asString()).with(user(alice)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(taskOn(alice, dossierId).get("assigneeId").asString()).isEqualTo(alice);
        assertThat(taskOn(bob, dossierId)).isNull();
    }

    private void known(String userId, Set<String> idpGroups) {
        knownUsers.upsert(new KnownUser(TENANT, userId, userId, null, Set.of("DOSSIER_MANAGER"), idpGroups,
                Instant.parse("2026-10-31T08:00:00Z")));
    }

    private void configure(String type, String process, String group, List<String> editors) throws Exception {
        RequestPostProcessor admin = admin();
        mockMvc.perform(post("/api/dossier-types").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","label":"Facture","workflowKey":"%s","active":true,"allowUnknownFields":false,
                                 "fieldDefinitions":[{"name":"objet","label":"Objet","type":"STRING","required":true}]}
                                """.formatted(type, process)))
                .andExpect(status().isCreated());
        for (String editor : editors) {
            mockMvc.perform(post("/api/dossier-types/{code}/acl", type).with(admin).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subjectUserId\":\"" + editor + "\",\"permission\":\"EDIT\"}"))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(post("/api/workflow-type-configs").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowKey":"%s","label":"Traiter une facture","onStartDossierStatus":"EN_COURS",
                                 "allowedDossierStatuses":["OUVERT","EN_COURS"],
                                 "statusDefinitions":[{"code":"OUVERTE","label":"Ouverte","initial":true,"outcome":"NONE"}]}
                                """.formatted(process)))
                .andExpect(status().isCreated());
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="%s" name="Traiter une facture" isExecutable="true">
                    <startEvent id="debut"/>
                    <userTask id="traiter" name="Traiter la facture" flowable:candidateGroups="%s"/>
                    <endEvent id="fin"/>
                    <sequenceFlow id="f1" sourceRef="debut" targetRef="traiter"/>
                    <sequenceFlow id="f2" sourceRef="traiter" targetRef="fin"/>
                  </process>
                </definitions>
                """.formatted(process, group);
        mockMvc.perform(multipart("/api/workflow-bpmn-deployments").file(new MockMultipartFile("file", process + ".bpmn20.xml",
                                "application/xml", bpmn.getBytes(StandardCharsets.UTF_8)))
                        .with(admin).with(csrf()))
                .andExpect(status().isCreated());
    }

    private String startWorkflow(String type, String process, String starter) throws Exception {
        String dossierId = JSON.readTree(mockMvc.perform(post("/api/dossiers").with(user(starter)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dossierTypeCode\":\"" + type + "\",\"label\":\"Facture 117\",\"fields\":{\"objet\":\"Facture 117\"}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        mockMvc.perform(patch("/api/dossiers/{id}/status", dossierId).with(user(starter)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OUVERT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId).with(user(starter)).with(csrf())
                        .header("Idempotency-Key", "grp-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processDefinitionKey\":\"" + process + "\"}"))
                .andExpect(status().isCreated());
        return dossierId;
    }

    /** La tâche du dossier dans « Tâches » de l'utilisateur, ou {@code null}. */
    private JsonNode taskOn(String userId, String dossierId) throws Exception {
        JsonNode page = JSON.readTree(mockMvc.perform(get("/api/workflow-tasks/mine").param("size", "100").with(user(userId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<JsonNode> found = new ArrayList<>();
        for (JsonNode task : page.get("content")) {
            if (dossierId.equals(task.get("dossierId").asString())) {
                found.add(task);
            }
        }
        assertThat(found).hasSizeLessThanOrEqualTo(1);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static RequestPostProcessor admin() {
        return oidcLogin().idToken(token -> token.subject("admin-taches").claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
    }

    private static RequestPostProcessor user(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_DOSSIER_MANAGER"));
    }
}
