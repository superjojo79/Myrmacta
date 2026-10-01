package com.flowforge.infrastructure.web.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.workflow.model.Provenance;
import com.flowforge.domain.workflow.model.ProposedWorkflowConfig;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.port.in.GetWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.in.ProposeDraftUseCase;
import com.flowforge.support.OidcClientTestConfiguration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** TODO §82 (D6) — « Brouillons à publier » à travers la vraie chaîne HTTP, Flowable et base H2. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class DraftsApiTest {

    private static final AccessContext.User AGENT = AccessContext.user("agent-1", Set.of("API_SERVICE"));
    private static final ProposedWorkflowConfig CONFIG = new ProposedWorkflowConfig("Brouillon test",
            List.of(new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE)),
            List.of(DossierStatus.OUVERT), null, List.of());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProposeDraftUseCase propose;

    @Autowired
    private GetWorkflowTypeConfigUseCase getConfig;

    private static RequestPostProcessor user(String id, String role) {
        return oidcLogin()
                .idToken(t -> t.subject(id).claim("tenant_id", "tenant_dev").claim("email", id + "@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static String key() {
        return "draftApi" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String bpmn(String key) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://flowforge/test">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/><userTask id="wait" name="Wait"/><endEvent id="end"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="wait"/><sequenceFlow id="f2" sourceRef="wait" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(key);
    }

    private String stamp(String key) throws Exception {
        var body = mockMvc.perform(get("/api/admin/drafts").with(user("alice", "TENANT_ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var node = tools.jackson.databind.json.JsonMapper.builder().build().readTree(body);
        for (var d : node) {
            if (key.equals(d.get("key").asText())) {
                return d.get("updatedAt").asText();
            }
        }
        throw new AssertionError("draft not listed: " + key);
    }

    @Test
    void aProposedDraftIsListedWithItsProvenanceAndPublishedByAHuman() throws Exception {
        String key = key();
        propose.proposeBpmn(AGENT, Provenance.MCP, key, bpmn(key), CONFIG, false);
        assertThat(getConfig.getByKey(key)).as("rien n'est appliqué avant la publication").isEmpty();

        mockMvc.perform(get("/api/admin/drafts").with(user("alice", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='%s')].provenance".formatted(key)).value("MCP"))
                .andExpect(jsonPath("$[?(@.key=='%s')].author".formatted(key)).value("agent-1"))
                .andExpect(jsonPath("$[?(@.key=='%s')].hasProposedConfig".formatted(key)).value(true));

        String reviewed = stamp(key);
        mockMvc.perform(post("/api/admin/drafts/BPMN/%s/publish".formatted(key)).with(user("alice", "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedUpdatedAt\":\"%s\"}".formatted(reviewed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MANUAL"))
                .andExpect(jsonPath("$.publishedBy").value("alice"))
                .andExpect(jsonPath("$.draftAuthor").value("agent-1"))
                .andExpect(jsonPath("$.draftProvenance").value("MCP"))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(getConfig.getByKey(key)).isPresent();
        mockMvc.perform(get("/api/admin/drafts").with(user("alice", "TENANT_ADMIN")))
                .andExpect(jsonPath("$[?(@.key=='%s')]".formatted(key)).isEmpty());
        mockMvc.perform(get("/api/admin/drafts/publications").with(user("alice", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.artifactKey=='%s')].publishedBy".formatted(key)).value("alice"));
    }

    @Test
    void aDraftChangedAfterReviewIsRefusedWith409() throws Exception {
        String key = key();
        propose.proposeBpmn(AGENT, Provenance.MCP, key, bpmn(key), CONFIG, false);
        String reviewed = stamp(key);
        Thread.sleep(5);
        propose.proposeBpmn(AGENT, Provenance.MCP, key, bpmn(key) + "<!-- v2 -->", CONFIG, false);

        mockMvc.perform(post("/api/admin/drafts/BPMN/%s/publish".formatted(key)).with(user("alice", "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedUpdatedAt\":\"%s\"}".formatted(reviewed)))
                .andExpect(status().isConflict());
        assertThat(getConfig.getByKey(key)).isEmpty();
    }

    @Test
    void publishingWithoutTheReviewedTimestampIsRefused() throws Exception {
        String key = key();
        propose.proposeBpmn(AGENT, Provenance.MCP, key, bpmn(key), CONFIG, false);
        mockMvc.perform(post("/api/admin/drafts/BPMN/%s/publish".formatted(key)).with(user("alice", "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdministratorsSeeAndPublish() throws Exception {
        mockMvc.perform(get("/api/admin/drafts").with(user("bob", "DOSSIER_VIEWER"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/drafts/BPMN/x/publish").with(user("bob", "DOSSIER_VIEWER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/drafts/BPMN/x").with(user("bob", "DOSSIER_VIEWER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void discardRemovesTheDraftThen404() throws Exception {
        String key = key();
        propose.proposeBpmn(AGENT, Provenance.MCP, key, bpmn(key), CONFIG, false);
        mockMvc.perform(delete("/api/admin/drafts/BPMN/%s".formatted(key)).with(user("alice", "WORKFLOW_ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/drafts/BPMN/%s".formatted(key)).with(user("alice", "WORKFLOW_ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aHumanEditingAnAgentDraftTakesItOverWithoutLosingTheProposedConfiguration() throws Exception {
        String key = key();
        propose.proposeBpmn(AGENT, Provenance.MCP, key, bpmn(key), CONFIG, false);

        mockMvc.perform(put("/api/workflow-bpmn-drafts/%s".formatted(key)).with(user("alice", "TENANT_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tools.jackson.databind.json.JsonMapper.builder().build()
                                .writeValueAsString(java.util.Map.of("xml", bpmn(key) + "<!-- edited -->"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/drafts").with(user("alice", "TENANT_ADMIN")))
                .andExpect(jsonPath("$[?(@.key=='%s')].provenance".formatted(key)).value("UI"))
                .andExpect(jsonPath("$[?(@.key=='%s')].author".formatted(key)).value("alice"))
                .andExpect(jsonPath("$[?(@.key=='%s')].hasProposedConfig".formatted(key)).value(true));
    }
}
