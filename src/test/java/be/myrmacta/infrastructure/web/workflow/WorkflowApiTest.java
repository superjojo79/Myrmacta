package be.myrmacta.infrastructure.web.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import be.myrmacta.domain.dossier.port.in.AttachWorkflowUseCase;
import be.myrmacta.domain.user.port.in.RecordUserLoginUseCase;
import java.util.Arrays;
import java.util.Set;
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

    @Autowired
    private AttachWorkflowUseCase attachWorkflow;

    @Autowired
    private RecordUserLoginUseCase recordUserLogin;

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
                .andExpect(jsonPath("$.startedBy").value("workflow-editor"))
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
                .andExpect(jsonPath("$.content[0].dossierId").value(dossierId))
                .andExpect(jsonPath("$.content[0].sequence").value(1));

        String processInstanceId = extractString(startResult.getResponse().getContentAsString(), "processInstanceId");
        String taskId = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult()
                .getId();

        mockMvc.perform(get("/api/workflow-links/{id}/diagram", linkId)
                        .with(user("workflow-editor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bpmnXml", containsString("<process id=\"smokeProcess\"")))
                .andExpect(jsonPath("$.activeIds[0]").value("smokeTask"));

        mockMvc.perform(get("/api/workflow-links/{id}/history", linkId)
                        .with(user("workflow-editor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].activityId", hasItem("startEvent")));

        mockMvc.perform(get("/api/workflow-links/{id}/tasks", linkId)
                        .with(user("workflow-editor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].taskId").value(taskId))
                .andExpect(jsonPath("$.content[0].name").value("Smoke Task"))
                // smokeTask carries no flowable:formKey (TODO §52) -- field present, null value.
                .andExpect(jsonPath("$.content[0].formKey").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/workflow-links/{id}/dossier", linkId)
                        .with(user("workflow-editor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dossier.id").value(dossierId))
                .andExpect(jsonPath("$.relations", hasSize(0)));

        mockMvc.perform(get("/api/workflow-links/{id}/diagram", linkId)
                        .with(user("non-participant")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/workflow-links/{id}/tasks/{taskId}/reassign", linkId, taskId)
                        .with(user("workflow-editor"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assigneeId": "assignee-sub"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/workflow-links/{id}/tasks/{taskId}/reassign", linkId, taskId)
                        .with(user("workflow-admin", "WORKFLOW_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assigneeId": "assignee-sub"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/workflow-links/{id}/tasks/{taskId}/reassign", linkId, taskId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assigneeId": "assignee-sub"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        // assigneeId must be a known participant/observer (best-effort guard, no user directory) —
        // configure assignee-sub as an observer of this workflow key before reassigning to it.
        updateWorkflowConfigObservers(processKey, "assignee-sub");

        mockMvc.perform(post("/api/workflow-links/{id}/tasks/{taskId}/reassign", linkId, taskId)
                        .with(user("workflow-admin", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assigneeId": "assignee-sub"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/workflow-links/{id}/tasks", linkId)
                        .with(user("assignee-sub")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].assignee").value("assignee-sub"));

        mockMvc.perform(get("/api/workflow-tasks/mine")
                        .with(user("assignee-sub")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].taskId").value(taskId))
                .andExpect(jsonPath("$.content[0].assigneeId").value("assignee-sub"))
                .andExpect(jsonPath("$.content[0].formKey").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));

        // TODO §57 H2: EDIT on the type is no longer enough once the task has an assignee.
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
                .andExpect(status().isForbidden());

        grant(typeCode, "assignee-sub", "EDIT");
        mockMvc.perform(post("/api/workflow-tasks/{taskId}/complete", taskId)
                        .with(user("assignee-sub"))
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
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].endReason").value("COMPLETED"));
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
    void complete_withDocumentTypeNotAllowedForTheDossierType_returns400AndNeverReachesTheEngine() throws Exception {
        // Phase 9 adversarial review finding E4: DossierType.documentTemplateKeys() was only
        // enforced at discovery time (GET .../document-templates) -- nothing on the completion
        // path stopped a task holder (any actor with EDIT on the dossier, not just the one who
        // opened the document-generation picker) from smuggling an arbitrary documentType into
        // ${documentGenerationTask}. createType() below configures no documentTemplateKeys at
        // all, so any documentType must be rejected.
        createWorkflowConfig("smokeProcess");
        String typeCode = createType();
        grant(typeCode, "workflow-editor", "EDIT");
        String dossierId = createDossier(typeCode, "workflow-editor");

        MvcResult startResult = mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId)
                        .with(user("workflow-editor"))
                        .with(csrf())
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processDefinitionKey": "smokeProcess"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
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
                                    "documentType": "some-other-tenants-secret-template"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult())
                .as("the task must still be active -- the engine must never see a rejected completion")
                .isNotNull();
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
    void visualizationReservedLinkAllowsDossierButDiagramReturns409() throws Exception {
        String processKey = "reservedProcess-" + UUID.randomUUID();
        createWorkflowConfig(processKey);
        String typeCode = createType();
        grant(typeCode, "reserved-starter", "EDIT");
        String dossierId = createDossier(typeCode, "reserved-starter");
        var reserved = attachWorkflow.reserve(
                UUID.fromString(dossierId), processKey, "manual:reserved", "reserved-starter");

        mockMvc.perform(get("/api/workflow-links/{id}/dossier", reserved.id())
                        .with(user("reserved-starter")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dossier.id").value(dossierId))
                .andExpect(jsonPath("$.relations", hasSize(0)));

        mockMvc.perform(get("/api/workflow-links/{id}/diagram", reserved.id())
                        .with(user("reserved-starter")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void visualizationDossierPanelIncludesRealRelations() throws Exception {
        String processKey = "smokeProcess";
        createWorkflowConfig(processKey);
        String sourceType = createType();
        String targetType = createType();
        grant(sourceType, "rel-viz", "EDIT");
        grant(targetType, "rel-viz", "EDIT");
        String sourceId = createDossier(sourceType, "rel-viz");
        String targetId = createDossier(targetType, "rel-viz");

        mockMvc.perform(post("/api/dossiers/{id}/relations", sourceId)
                        .with(user("rel-viz"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetDossierId": "%s",
                                  "type": "LINKED"
                                }
                                """.formatted(targetId)))
                .andExpect(status().isCreated());

        MvcResult startResult = mockMvc.perform(post("/api/dossiers/{id}/workflows", sourceId)
                        .with(user("rel-viz"))
                        .with(csrf())
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processDefinitionKey": "%s"
                                }
                                """.formatted(processKey)))
                .andExpect(status().isCreated())
                .andReturn();
        String linkId = extractString(startResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/workflow-links/{id}/dossier", linkId)
                        .with(user("rel-viz")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dossier.id").value(sourceId))
                .andExpect(jsonPath("$.relations", hasSize(1)))
                .andExpect(jsonPath("$.relations[0].type").value("LINKED"))
                .andExpect(jsonPath("$.relations[0].relatedDossier.id").value(targetId));
    }

    @Test
    void workflowTypeConfigObserverUserIdsRoundTripAndAuthorizeVisualization() throws Exception {
        String processKey = "smokeProcess";
        createWorkflowConfig(processKey);
        updateWorkflowConfigObservers(processKey, "observer-sub");
        mockMvc.perform(get("/api/workflow-type-configs/{workflowKey}", processKey)
                        .with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observerUserIds[0]").value("observer-sub"));

        mockMvc.perform(get("/api/workflow-type-configs").with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.workflowKey=='%s')]".formatted(processKey)).isNotEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/workflow-type-configs")
                        .param("size", "101")
                        .with(user("reader")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/workflow-type-configs")
                        .param("sort", "label,asc")
                        .with(user("reader")))
                .andExpect(status().isBadRequest());

        String typeCode = createType();
        grant(typeCode, "observer-starter", "EDIT");
        String dossierId = createDossier(typeCode, "observer-starter");
        MvcResult startResult = mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId)
                        .with(user("observer-starter"))
                        .with(csrf())
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processDefinitionKey": "%s"
                                }
                                """.formatted(processKey)))
                .andExpect(status().isCreated())
                .andReturn();
        String linkId = extractString(startResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/workflow-links/{id}/diagram", linkId)
                        .with(user("observer-sub")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bpmnXml").isNotEmpty());

        // Being a workflow observer grants visibility into process state (diagram/history/tasks)
        // but not the dossier's business fields: the dossier panel additionally enforces the
        // type-level VIEW ACL, and observer-sub was never granted one for this dossier type.
        mockMvc.perform(get("/api/workflow-links/{id}/dossier", linkId)
                        .with(user("observer-sub")))
                .andExpect(status().isForbidden());

        grant(typeCode, "observer-sub", "VIEW");
        mockMvc.perform(get("/api/workflow-links/{id}/dossier", linkId)
                        .with(user("observer-sub")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dossier.id").value(dossierId))
                .andExpect(jsonPath("$.relations", hasSize(0)));
    }

    @Test
    void visualizationUnknownLinkReturns404AndTenantAdminBypassesParticipantCheck() throws Exception {
        mockMvc.perform(get("/api/workflow-links/{id}/diagram", UUID.randomUUID())
                        .with(user("reader")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        String processKey = "smokeProcess";
        createWorkflowConfig(processKey);
        String typeCode = createType();
        grant(typeCode, "viz-owner", "EDIT");
        String dossierId = createDossier(typeCode, "viz-owner");
        MvcResult startResult = mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId)
                        .with(user("viz-owner"))
                        .with(csrf())
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processDefinitionKey": "%s"
                                }
                                """.formatted(processKey)))
                .andExpect(status().isCreated())
                .andReturn();
        String linkId = extractString(startResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/workflow-links/{id}/diagram", linkId)
                        .with(user("tenant-admin-bypass", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bpmnXml").isNotEmpty());

        mockMvc.perform(post("/api/workflow-links/{id}/tasks/{taskId}/reassign", linkId, "missing-task")
                        .with(user("tenant-admin-bypass", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assigneeId": "viz-owner"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void claimLetsDossierEditorTakeAFreeTaskAndAssignableUsersFeedTheReassignment() throws Exception {
        // D-ASSIGN-CLAIM / D-ASSIGN-DIR
        String processKey = "smokeProcess";
        createWorkflowConfig(processKey);
        String typeCode = createType();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String starter = "claim-starter-" + suffix;
        String editor = "claim-editor-" + suffix;
        String lateEditor = "claim-late-" + suffix;
        String viewer = "claim-viewer-" + suffix;
        grant(typeCode, starter, "EDIT");
        grant(typeCode, editor, "EDIT");
        grant(typeCode, lateEditor, "EDIT");
        grant(typeCode, viewer, "VIEW");
        String dossierId = createDossier(typeCode, starter);
        MvcResult startResult = mockMvc.perform(post("/api/dossiers/{id}/workflows", dossierId)
                        .with(user(starter))
                        .with(csrf())
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processDefinitionKey\": \"%s\"}".formatted(processKey)))
                .andExpect(status().isCreated())
                .andReturn();
        String linkId = extractString(startResult.getResponse().getContentAsString(), "id");
        String taskId = taskService.createTaskQuery()
                .processInstanceId(extractString(startResult.getResponse().getContentAsString(), "processInstanceId"))
                .singleResult()
                .getId();

        // Un éditeur qui n'a pas encore participé voit la tâche libre…
        mockMvc.perform(get("/api/workflow-links/{id}/tasks", linkId).with(user(editor, "DOSSIER_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].assignee").value(org.hamcrest.Matchers.nullValue()));
        // …un simple lecteur non, et il ne peut pas la prendre.
        mockMvc.perform(get("/api/workflow-links/{id}/tasks", linkId).with(user(viewer, "DOSSIER_VIEWER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/workflow-links/{id}/tasks/{taskId}/claim", linkId, taskId)
                        .with(user(viewer, "DOSSIER_VIEWER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/workflow-links/{id}/tasks/{taskId}/claim", linkId, taskId)
                        .with(user(editor, "DOSSIER_MANAGER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/workflow-tasks/mine").with(user(editor, "DOSSIER_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].taskId").value(taskId));
        // Reprendre sa propre tâche est sans effet ; prendre celle d'un autre est un conflit.
        mockMvc.perform(post("/api/workflow-links/{id}/tasks/{taskId}/claim", linkId, taskId)
                        .with(user(editor, "DOSSIER_MANAGER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/workflow-links/{id}/tasks/{taskId}/claim", linkId, taskId)
                        .with(user(lateEditor, "DOSSIER_MANAGER"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        recordUserLogin.record(lateEditor, "Late Editor", lateEditor + "@example.com", Set.of("DOSSIER_MANAGER"));
        recordUserLogin.record(viewer, "Viewer", null, Set.of("DOSSIER_VIEWER"));
        mockMvc.perform(get("/api/workflow-links/{id}/assignable-users", linkId).with(user(editor, "DOSSIER_MANAGER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/workflow-links/{id}/assignable-users", linkId)
                        .with(user("claim-admin-" + suffix, "WORKFLOW_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].userId", hasItem(lateEditor)))
                .andExpect(jsonPath("$[*].userId", org.hamcrest.Matchers.not(hasItem(viewer))))
                .andExpect(jsonPath("$[?(@.userId == '%s')].displayName".formatted(lateEditor)).value("Late Editor"));

        // Un utilisateur connu qui peut modifier le dossier est un assigné valide sans être participant.
        mockMvc.perform(post("/api/workflow-links/{id}/tasks/{taskId}/reassign", linkId, taskId)
                        .with(user("claim-admin-" + suffix, "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"%s\"}".formatted(lateEditor)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/workflow-links/{id}/tasks/{taskId}/reassign", linkId, taskId)
                        .with(user("claim-admin-" + suffix, "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"%s\"}".formatted(viewer)))
                .andExpect(status().isBadRequest());
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
        createWorkflowConfig(workflowKey, new String[0]);
    }

    private void createWorkflowConfig(String workflowKey, String... observerUserIds) throws Exception {
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
                                  "onStartDossierStatus": null,
                                  "observerUserIds": %s
                                }
                                """.formatted(workflowKey, observerJson(observerUserIds))))
                .andReturn();
        int status = result.getResponse().getStatus();
        if (status != 201 && status != 409) {
            throw new AssertionError("Expected workflow config seed to return 201 or 409 but was " + status
                    + ": " + result.getResponse().getContentAsString());
        }
    }

    private static String observerJson(String... observerUserIds) {
        if (observerUserIds == null || observerUserIds.length == 0) {
            return "[]";
        }
        return "[" + Arrays.stream(observerUserIds)
                .map(id -> "\"" + id + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private void updateWorkflowConfigObservers(String workflowKey, String... observerUserIds) throws Exception {
        mockMvc.perform(patch("/api/workflow-type-configs/{workflowKey}", workflowKey)
                        .with(user("workflow-admin-" + UUID.randomUUID(), "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
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
                                  "onStartDossierStatus": null,
                                  "observerUserIds": %s
                                }
                                """.formatted(observerJson(observerUserIds))))
                .andExpect(status().isOk());
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
