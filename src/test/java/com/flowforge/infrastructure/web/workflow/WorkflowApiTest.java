package com.flowforge.infrastructure.web.workflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.support.OidcClientTestConfiguration;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.flowable.engine.TaskService;
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
class WorkflowApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskService taskService;

    @Test
    void configCrudAndManualStartExposeHistory() throws Exception {
        String processKey = "smokeProcess";
        createWorkflowConfig(processKey);
        String typeCode = createType();
        grant(typeCode, "workflow-editor", "EDIT");
        String dossierId = createDossier(typeCode, "workflow-editor");
        String idempotencyKey = "idem-" + UUID.randomUUID();

        MvcResult startResult = mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId)
                        .with(user("workflow-editor"))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processDefinitionKey": "%s",
                                  "variables": {
                                    "priority": "NORMAL"
                                  }
                                }
                                """.formatted(processKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dossierId").value(dossierId))
                .andExpect(jsonPath("$.processDefinitionKey").value(processKey))
                .andExpect(jsonPath("$.processInstanceId").isNotEmpty())
                .andReturn();
        String linkId = extractString(startResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId)
                        .with(user("workflow-editor"))
                        .with(csrf())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processDefinitionKey": "%s"
                                }
                                """.formatted(processKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(linkId));

        mockMvc.perform(get("/api/dossiers/{id}/workflows", dossierId)
                        .with(user("workflow-editor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dossierId").value(dossierId))
                .andExpect(jsonPath("$[0].sequence").value(1));

        String processInstanceId = extractString(startResult.getResponse().getContentAsString(), "processInstanceId");
        String taskId = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult()
                .getId();
        mockMvc.perform(post("/api/workflow-tasks/{taskId}/complete", taskId)
                        .with(user("workflow-editor"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "variables": {
                                    "targetWorkflowStatus": "COMPLETED"
                                  }
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/dossiers/{id}/workflows", dossierId)
                        .with(user("workflow-editor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].endReason").value("COMPLETED"));
    }

    @Test
    void start_anonymousReturns401AndWithoutEditReturns403() throws Exception {
        createWorkflowConfig("smokeProcess");
        String typeCode = createType();
        grant(typeCode, "workflow-owner", "EDIT");
        String dossierId = createDossier(typeCode, "workflow-owner");

        mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId)
                        .with(csrf())
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processDefinitionKey": "smokeProcess"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId)
                        .with(user("no-edit-user"))
                        .with(csrf())
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processDefinitionKey": "smokeProcess"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void start_secondDifferentIdempotencyKeyWhileActiveReturns409() throws Exception {
        createWorkflowConfig("smokeProcess");
        String typeCode = createType();
        grant(typeCode, "workflow-collision", "EDIT");
        String dossierId = createDossier(typeCode, "workflow-collision");

        mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId)
                        .with(user("workflow-collision"))
                        .with(csrf())
                        .header("Idempotency-Key", "idem-first-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processDefinitionKey": "smokeProcess"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId)
                        .with(user("workflow-collision"))
                        .with(csrf())
                        .header("Idempotency-Key", "idem-second-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processDefinitionKey": "smokeProcess"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void workflowTypeConfigMutationRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/workflow-type-configs")
                        .with(user("plain-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workflowKey": "deniedProcess",
                                  "label": "Denied",
                                  "statusDefinitions": [
                                    {
                                      "code": "STARTED",
                                      "label": "Started",
                                      "initial": true,
                                      "outcome": "NONE"
                                    }
                                  ],
                                  "allowedDossierStatuses": ["OUVERT"]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void manualStartRequiresIdempotencyKeyAndRejectsReservedVariables() throws Exception {
        createWorkflowConfig("smokeProcess");
        String typeCode = createType();
        grant(typeCode, "workflow-editor-reserved", "EDIT");
        String dossierId = createDossier(typeCode, "workflow-editor-reserved");

        mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId)
                        .with(user("workflow-editor-reserved"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processDefinitionKey": "smokeProcess"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId)
                        .with(user("workflow-editor-reserved"))
                        .with(csrf())
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processDefinitionKey": "smokeProcess",
                                  "variables": {
                                    "dossierId": "client-forbidden"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private void createWorkflowConfig(String workflowKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workflow-type-configs")
                        .with(user("workflow-admin-" + UUID.randomUUID(), "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workflowKey": "%s",
                                  "label": "Smoke",
                                  "statusDefinitions": [
                                    {
                                      "code": "STARTED",
                                      "label": "Started",
                                      "initial": true,
                                      "outcome": "NONE"
                                    },
                                    {
                                      "code": "COMPLETED",
                                      "label": "Completed",
                                      "initial": false,
                                      "outcome": "COMPLETED"
                                    }
                                  ],
                                  "allowedDossierStatuses": ["OUVERT", "EN_COURS", "SUSPENDU"],
                                  "onStartDossierStatus": null
                                }
                                """.formatted(workflowKey)))
                .andReturn();
        int status = result.getResponse().getStatus();
        if (status != 201 && status != 409) {
            throw new AssertionError("Expected workflow config seed to return 201 or 409 but was " + status
                    + ": " + result.getResponse().getContentAsString());
        }
    }

    private String createType() throws Exception {
        String code = "WF" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-" + code, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "label": "Label %s",
                                  "workflowKey": "workflow-%s",
                                  "active": true,
                                  "allowUnknownFields": false,
                                  "fieldDefinitions": []
                                }
                                """.formatted(code, code, code.toLowerCase())))
                .andExpect(status().isCreated());
        return code;
    }

    private void grant(String code, String userId, String permission) throws Exception {
        mockMvc.perform(post("/api/dossier-types/{code}/acl", code)
                        .with(user("acl-admin-" + code, "TENANT_ADMIN"))
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
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dossierTypeCode": "%s",
                                  "label": "Workflow dossier",
                                  "fields": {}
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andReturn();
        String dossierId = extractString(result.getResponse().getContentAsString(), "id");
        mockMvc.perform(patch("/api/dossiers/{id}/status", dossierId)
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "OUVERT"
                                }
                                """))
                .andExpect(status().isOk());
        return dossierId;
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

    private static String extractString(String json, String field) {
        var matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Field not found: " + field + " in " + json);
        }
        return matcher.group(1);
    }
}
