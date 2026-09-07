package com.flowforge.infrastructure.web.workflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.support.OidcClientTestConfiguration;
import java.util.Arrays;
import java.util.UUID;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Covers TODO §55 (questions-utilisateur-taches): admin CRUD RBAC and the runtime endpoint's
 * D-USERQ-RUNTIME-SCOPE guard, both against the real Spring Security chain (not
 * {@code standaloneSetup}) -- leçon retenue de connecteur-callback F0 (TODO §51), une route peut
 * être inatteignable en production malgré des tests verts qui contournent la vraie chaîne.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class TaskQuestionSchemaApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TenantProperties tenantProperties;

    @Test
    void create_asWorkflowAdmin_succeeds_andListGetUpdateDeleteWork() throws Exception {
        String schemaKey = uniqueKey();

        mockMvc.perform(post("/api/task-question-schemas")
                        .with(user("workflow-admin-1", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(schemaKey, "Orientation")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaKey").value(schemaKey))
                .andExpect(jsonPath("$.createdBy").value("workflow-admin-1"));

        mockMvc.perform(get("/api/task-question-schemas/{schemaKey}", schemaKey)
                        .with(user("workflow-admin-1", "WORKFLOW_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Orientation"));

        mockMvc.perform(get("/api/task-question-schemas")
                        .with(user("workflow-admin-1", "WORKFLOW_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.schemaKey=='%s')]".formatted(schemaKey)).exists());

        mockMvc.perform(put("/api/task-question-schemas/{schemaKey}", schemaKey)
                        .with(user("workflow-admin-1", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "Orientation revised",
                                  "fieldDefinitions": [{
                                    "name": "decision",
                                    "label": "Decision",
                                    "type": "SELECT",
                                    "required": true,
                                    "options": ["approve", "reject"],
                                    "group": null,
                                    "sensitive": false
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Orientation revised"));

        mockMvc.perform(delete("/api/task-question-schemas/{schemaKey}", schemaKey)
                        .with(user("workflow-admin-1", "WORKFLOW_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/task-question-schemas/{schemaKey}", schemaKey)
                        .with(user("workflow-admin-1", "WORKFLOW_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_withoutAdminRole_returns403_andAnonymousReturns401() throws Exception {
        String schemaKey = uniqueKey();

        mockMvc.perform(post("/api/task-question-schemas")
                        .with(user("plain-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(schemaKey, "Orientation")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/task-question-schemas")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(schemaKey, "Orientation")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withBlankSchemaKey_returns400() throws Exception {
        mockMvc.perform(post("/api/task-question-schemas")
                        .with(user("workflow-admin-2", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schemaKey": "",
                                  "label": "Orientation",
                                  "fieldDefinitions": [{
                                    "name": "decision",
                                    "label": "Decision",
                                    "type": "SELECT",
                                    "required": true,
                                    "options": ["approve", "reject"],
                                    "group": null,
                                    "sensitive": false
                                  }]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withDuplicateSchemaKeyForTheSameTenant_returns409() throws Exception {
        // Phase 8 coverage review: relies on the DB unique constraint (uq_task_question_schema_
        // tenant_key) + the generic DataIntegrityViolationException -> 409 handler, same pattern as
        // DossierType -- not exercised by any test until now.
        String schemaKey = uniqueKey();
        mockMvc.perform(post("/api/task-question-schemas")
                        .with(user("workflow-admin-dup", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(schemaKey, "Orientation")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/task-question-schemas")
                        .with(user("workflow-admin-dup", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(schemaKey, "Orientation again")))
                .andExpect(status().isConflict());
    }

    @Test
    void update_unknownSchemaKey_returns404() throws Exception {
        mockMvc.perform(put("/api/task-question-schemas/{schemaKey}", "does-not-exist-" + uniqueKey())
                        .with(user("workflow-admin-3b", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "New label",
                                  "fieldDefinitions": [{
                                    "name": "decision",
                                    "label": "Decision",
                                    "type": "SELECT",
                                    "required": true,
                                    "options": ["approve", "reject"],
                                    "group": null,
                                    "sensitive": false
                                  }]
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void runtime_returnsSchemaWithoutAuditFields_whenTaskReferencesIt_andRejectsAMismatchedSchemaKey()
            throws Exception {
        String schemaKey = "orientation-dossier";
        // Idempotent-ish: reuse the schema seeded by UserQuestionFormSmokeIT/the fixture if
        // already present, else create it fresh -- schemaKey is fixed by the BPMN fixture's
        // formKey, cannot be uniquified per test run.
        mockMvc.perform(post("/api/task-question-schemas")
                .with(user("workflow-admin-3", "WORKFLOW_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(schemaKey, "Orientation")));

        runtimeService.startProcessInstanceByKeyAndTenantId(
                "userQuestionSmoke", "user-question-api-" + UUID.randomUUID(), TENANT_ID);
        Task task = taskService.createTaskQuery()
                .processDefinitionKey("userQuestionSmoke")
                .active()
                .orderByTaskCreateTime()
                .desc()
                .list()
                .get(0);

        mockMvc.perform(get("/api/task-question-schemas/{schemaKey}/runtime", schemaKey)
                        .param("taskId", task.getId())
                        .with(user("any-task-holder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaKey").value(schemaKey))
                .andExpect(jsonPath("$.createdBy").doesNotExist());

        taskService.setAssignee(task.getId(), "alice");

        mockMvc.perform(get("/api/task-question-schemas/{schemaKey}/runtime", schemaKey)
                        .param("taskId", task.getId())
                        .with(user("any-task-holder")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/task-question-schemas/{schemaKey}/runtime", schemaKey)
                        .param("taskId", task.getId())
                        .with(user("alice")))
                .andExpect(status().isOk());

        // M8 checks assignment before formKey match: a non-assignee still gets 403 even with
        // a mismatched schema key. The 404 (unknown schema for this task) is for the assignee.
        mockMvc.perform(get("/api/task-question-schemas/{schemaKey}/runtime", "some-other-schema")
                        .param("taskId", task.getId())
                        .with(user("any-task-holder")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/task-question-schemas/{schemaKey}/runtime", "some-other-schema")
                        .param("taskId", task.getId())
                        .with(user("alice")))
                .andExpect(status().isNotFound());

        taskService.complete(task.getId(), java.util.Map.of("decision", "approve"));
    }

    private static String createBody(String schemaKey, String label) {
        return """
                {
                  "schemaKey": "%s",
                  "label": "%s",
                  "fieldDefinitions": [{
                    "name": "decision",
                    "label": "Decision",
                    "type": "SELECT",
                    "required": true,
                    "options": ["approve", "reject"],
                    "group": null,
                    "sensitive": false
                  }]
                }
                """.formatted(schemaKey, label);
    }

    private static String uniqueKey() {
        return "schema-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static RequestPostProcessor user(String userId, String... roles) {
        return oidcLogin()
                .idToken(token -> token
                        .subject(userId)
                        .claim("tenant_id", TENANT_ID)
                        .claim("email", userId + "@example.com")
                        .claim("name", userId))
                .authorities(Arrays.stream(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
