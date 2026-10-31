package be.myrmacta.infrastructure.web.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.flowable.engine.TaskService;
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
 * TODO §113 (issue #217, lot 2c, D-GROUP-REASSIGN) — réaffecter une tâche à un autre groupe par la vraie chaîne HTTP et
 * sécurité : un éditeur du dossier le peut, la tâche suivante suit le nouveau groupe, une note trace le changement ; un
 * utilisateur sans droit d'édition est refusé, un groupe inconnu aussi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class GroupReassignApiTest {

    private static final String TENANT = "tenant_dev";
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Autowired private MockMvc mockMvc;
    @Autowired private TaskService taskService;

    @Test
    void anEditorSendsTheTaskToAnotherGroup_theNextTaskFollows_andTheDossierKeepsATrace() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String sorting = "TRI_" + suffix;
        String legal = "JUR_" + suffix;
        String type = "COURRIER_" + suffix;
        String process = "trier" + suffix;
        String editor = "operateur-" + suffix;
        String outsider = "lecteur-" + suffix;
        group(sorting, "File de tri");
        group(legal, "Juridique");
        configure(type, process, sorting, editor);
        String dossierId = startWorkflow(type, process, editor);
        JsonNode link = read(get("/api/dossiers/{id}/workflows", dossierId), editor).get("content").get(0);
        String linkId = link.get("id").asString();
        JsonNode task = read(get("/api/workflow-links/{linkId}/tasks", linkId), editor).get("content").get(0);
        assertThat(task.get("candidateGroups").toString()).isEqualTo("[\"" + sorting + "\"]");
        String taskId = task.get("taskId").asString();

        mockMvc.perform(post("/api/workflow-links/{linkId}/tasks/{taskId}/reassign-group", linkId, taskId).with(user(outsider)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"groupCode\":\"" + legal + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/workflow-links/{linkId}/tasks/{taskId}/reassign-group", linkId, taskId).with(user(editor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"groupCode\":\"INCONNU_" + suffix + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/workflow-links/{linkId}/tasks/{taskId}/reassign-group", linkId, taskId).with(user(editor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"groupCode\":\"" + legal + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(read(get("/api/workflow-links/{linkId}/tasks", linkId), editor).get("content").get(0).get("candidateGroups").toString())
                .isEqualTo("[\"" + legal + "\"]");
        assertThat(read(get("/api/dossiers/{id}/elements", dossierId), editor).toString())
                .contains("NOTE", "Tâche « Trier » réaffectée de " + sorting + " à " + legal);

        taskService.complete(taskId);
        JsonNode next = read(get("/api/workflow-links/{linkId}/tasks", linkId), editor).get("content").get(0);
        assertThat(next.get("name").asString()).isEqualTo("Répondre");
        assertThat(next.get("candidateGroups").toString()).isEqualTo("[\"" + legal + "\"]");

        mockMvc.perform(get("/api/user-group-options").with(user(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '" + legal + "')].label").value("Juridique"))
                .andExpect(jsonPath("$[0].members").doesNotExist());
        mockMvc.perform(get("/api/user-group-options")).andExpect(status().isUnauthorized());
    }

    private void group(String code, String label) throws Exception {
        mockMvc.perform(post("/api/user-groups").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"label\":\"" + label + "\"}"))
                .andExpect(status().isCreated());
    }

    /** Le premier groupe est littéral dans le BPMN ; la seconde tâche ne nomme aucun groupe et suit celui du lien. */
    private void configure(String type, String process, String sortingGroup, String editor) throws Exception {
        RequestPostProcessor admin = admin();
        mockMvc.perform(post("/api/dossier-types").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","label":"Courrier","workflowKey":"%s","active":true,"allowUnknownFields":false,
                                 "fieldDefinitions":[{"name":"objet","label":"Objet","type":"STRING","required":true}]}
                                """.formatted(type, process)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/dossier-types/{code}/acl", type).with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectUserId\":\"" + editor + "\",\"permission\":\"EDIT\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/workflow-type-configs").with(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowKey":"%s","label":"Trier","onStartDossierStatus":"EN_COURS",
                                 "allowedDossierStatuses":["OUVERT","EN_COURS"],
                                 "statusDefinitions":[{"code":"OUVERT","label":"Ouvert","initial":true,"outcome":"NONE"}]}
                                """.formatted(process)))
                .andExpect(status().isCreated());
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://myrmacta/test">
                  <process id="%s" name="Trier" isExecutable="true">
                    <startEvent id="debut"/>
                    <userTask id="trier" name="Trier" flowable:candidateGroups="%s"/>
                    <userTask id="repondre" name="Répondre"/>
                    <endEvent id="fin"/>
                    <sequenceFlow id="f1" sourceRef="debut" targetRef="trier"/>
                    <sequenceFlow id="f2" sourceRef="trier" targetRef="repondre"/>
                    <sequenceFlow id="f3" sourceRef="repondre" targetRef="fin"/>
                  </process>
                </definitions>
                """.formatted(process, sortingGroup);
        mockMvc.perform(multipart("/api/workflow-bpmn-deployments").file(new MockMultipartFile("file", process + ".bpmn20.xml",
                                "application/xml", bpmn.getBytes(StandardCharsets.UTF_8)))
                        .with(admin).with(csrf()))
                .andExpect(status().isCreated());
    }

    private String startWorkflow(String type, String process, String starter) throws Exception {
        String dossierId = JSON.readTree(mockMvc.perform(post("/api/dossiers").with(user(starter)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dossierTypeCode\":\"" + type + "\",\"label\":\"Courrier 1\",\"fields\":{\"objet\":\"Question\"}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        mockMvc.perform(patch("/api/dossiers/{id}/status", dossierId).with(user(starter)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OUVERT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId).with(user(starter)).with(csrf())
                        .header("Idempotency-Key", "reg-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processDefinitionKey\":\"" + process + "\"}"))
                .andExpect(status().isCreated());
        return dossierId;
    }

    private JsonNode read(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String userId)
            throws Exception {
        return JSON.readTree(mockMvc.perform(request.with(user(userId))).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private static RequestPostProcessor admin() {
        return oidcLogin().idToken(token -> token.subject("admin-reaffectation").claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
    }

    private static RequestPostProcessor user(String subject) {
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_DOSSIER_MANAGER"));
    }
}
