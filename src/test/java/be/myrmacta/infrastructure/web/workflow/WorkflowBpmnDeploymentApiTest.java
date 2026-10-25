package be.myrmacta.infrastructure.web.workflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class WorkflowBpmnDeploymentApiTest {

    private static final String TENANT_ID = "tenant_dev";

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         targetNamespace="http://flowforge/test">
              <process id="%s" isExecutable="true">
                <startEvent id="start"/>
                <userTask id="wait" name="Wait"/>
                <endEvent id="end"/>
                <sequenceFlow id="flow1" sourceRef="start" targetRef="wait"/>
                <sequenceFlow id="flow2" sourceRef="wait" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deploy_withoutAdminRole_returns403() throws Exception {
        MockMultipartFile file = bpmnFile("apiDenied" + System.nanoTime());

        mockMvc.perform(multipart("/api/workflow-bpmn-deployments").file(file).with(user("plain-user")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deploy_validBpmnWithKnownKey_returns201WithDeploymentResult() throws Exception {
        String key = "apiKnown" + System.nanoTime();
        createWorkflowTypeConfig(key);
        MockMultipartFile file = bpmnFile(key);

        mockMvc.perform(multipart("/api/workflow-bpmn-deployments")
                        .file(file)
                        .with(user("admin-deploy", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.processDefinitionKey").value(key))
                .andExpect(jsonPath("$.deployedVersion").value(1))
                .andExpect(jsonPath("$.deploymentId").isNotEmpty());
    }

    @Test
    void deploy_invalidBpmn_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.bpmn20.xml", "application/xml", "<not-even-xml".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/workflow-bpmn-deployments")
                        .file(file)
                        .with(user("admin-invalid", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deploy_disallowedContentType_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "diagram.bpmn20.xml", "application/x-sh", "echo hi".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/workflow-bpmn-deployments")
                        .file(file)
                        .with(user("admin-mime", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deploy_unknownWorkflowKey_returns404() throws Exception {
        MockMultipartFile file = bpmnFile("apiUnknown" + System.nanoTime());

        mockMvc.perform(multipart("/api/workflow-bpmn-deployments")
                        .file(file)
                        .with(user("admin-unknown", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deploy_blankOriginalFilename_stillDeploysInsteadOfCrashing() throws Exception {
        String key = "apiBlankFilename" + System.nanoTime();
        createWorkflowTypeConfig(key);
        // MockMultipartFile normalizes a null originalFilename to "" — this reproduces the blank case;
        // a real servlet-backed multipart request can hand the controller an actual null (Content-Disposition
        // "filename" parameter omitted), which the adapter must equally not crash on.
        MockMultipartFile file = new MockMultipartFile("file", null, "application/xml",
                BPMN.formatted(key).getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/workflow-bpmn-deployments")
                        .file(file)
                        .with(user("admin-blank-filename", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.processDefinitionKey").value(key));
    }

    @Test
    void deploy_emptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.bpmn20.xml", "application/xml", new byte[0]);

        mockMvc.perform(multipart("/api/workflow-bpmn-deployments")
                        .file(file)
                        .with(user("admin-empty", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void info_deployedKey_returns200WithInfo() throws Exception {
        String key = "apiInfo" + System.nanoTime();
        createWorkflowTypeConfig(key);
        mockMvc.perform(multipart("/api/workflow-bpmn-deployments")
                        .file(bpmnFile(key))
                        .with(user("admin-info", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/workflow-bpmn-deployments/info")
                        .param("processDefinitionKey", key)
                        .with(user("admin-info-get", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processDefinitionKey").value(key))
                .andExpect(jsonPath("$.latestVersion").value(1));
    }

    @Test
    void info_neverDeployedKey_returns204() throws Exception {
        mockMvc.perform(get("/api/workflow-bpmn-deployments/info")
                        .param("processDefinitionKey", "apiNeverDeployed" + System.nanoTime())
                        .with(user("admin-info-empty", "TENANT_ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deployedXml_deployedKey_returns200WithXmlBody() throws Exception {
        String key = "apiXml" + System.nanoTime();
        createWorkflowTypeConfig(key);
        mockMvc.perform(multipart("/api/workflow-bpmn-deployments")
                        .file(bpmnFile(key))
                        .with(user("admin-xml", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/workflow-bpmn-deployments/{key}/xml", key)
                        .with(user("admin-xml-get", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"" + key + "\"")));
    }

    @Test
    void deployedXml_neverDeployedKey_returns204() throws Exception {
        mockMvc.perform(get("/api/workflow-bpmn-deployments/{key}/xml", "apiXmlNeverDeployed" + System.nanoTime())
                        .with(user("admin-xml-empty", "TENANT_ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deployedXml_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/workflow-bpmn-deployments/{key}/xml", "apiXmlDenied" + System.nanoTime())
                        .with(user("plain-user-xml")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deploy_successfulOnKeyWithExistingDraft_deletesTheDraft() throws Exception {
        String key = "apiDeployDeletesDraft" + System.nanoTime();
        createWorkflowTypeConfig(key);
        mockMvc.perform(put("/api/workflow-bpmn-drafts/{key}", key)
                        .with(user("admin-draft-before-deploy", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xml\": \"" + BPMN.formatted(key).replace("\n", "\\n").replace("\"", "\\\"")
                                + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/workflow-bpmn-deployments")
                        .file(bpmnFile(key))
                        .with(user("admin-deploy-deletes-draft", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/workflow-bpmn-drafts/{key}", key)
                        .with(user("admin-draft-after-deploy", "TENANT_ADMIN")))
                .andExpect(status().isNoContent());
    }

    private static final String BLOCK_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://flowforge/test">
              <process id="%s" isExecutable="true">
                <startEvent id="start"/>
                <serviceTask id="setStatus" flowable:delegateExpression="${updateWorkflowLinkStatusTask}"
                             flowable:targetWorkflowStatus="%s"/>
                <userTask id="wait" name="Wait"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="setStatus"/>
                <sequenceFlow id="f2" sourceRef="setStatus" targetRef="wait"/>
              </process>
            </definitions>
            """;

    private static MockMultipartFile blockFile(String key, String status) {
        return new MockMultipartFile("file", "diagram.bpmn20.xml", "application/xml",
                BLOCK_BPMN.formatted(key, status).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void deploy_blockAttributeReferencingAStatusOfThisWorkflow_isAccepted() throws Exception {
        // S-REFCHK-3 : le statut est vérifié contre la configuration DE CE process
        String key = "apiBlockOk" + System.nanoTime();
        createWorkflowTypeConfig(key);

        mockMvc.perform(multipart("/api/workflow-bpmn-deployments")
                        .file(blockFile(key, "STARTED"))
                        .with(user("admin-block-ok", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    void deploy_blockAttributeReferencingAnUnknownStatus_returns404AndDeploysNothing() throws Exception {
        // S-REFCHK-2
        String key = "apiBlockKo" + System.nanoTime();
        createWorkflowTypeConfig(key);

        mockMvc.perform(multipart("/api/workflow-bpmn-deployments")
                        .file(blockFile(key, "INCONNU"))
                        .with(user("admin-block-ko", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/workflow-bpmn-deployments/info")
                        .param("processDefinitionKey", key)
                        .with(user("admin-block-ko-info", "TENANT_ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deploy_blockAttributeWithAnExpression_returns400() throws Exception {
        // S-PARSE-3 / T-SEC-2 de bout en bout
        String key = "apiBlockUel" + System.nanoTime();
        createWorkflowTypeConfig(key);

        mockMvc.perform(multipart("/api/workflow-bpmn-deployments")
                        .file(blockFile(key, "${T(java.lang.Runtime).getRuntime()}"))
                        .with(user("admin-block-uel", "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    private void createWorkflowTypeConfig(String key) throws Exception {
        mockMvc.perform(post("/api/workflow-type-configs")
                        .with(user("admin-config-" + key, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workflowKey": "%s",
                                  "label": "Label %s",
                                  "statusDefinitions": [
                                    {"code": "STARTED", "label": "Started", "initial": true, "outcome": "NONE"}
                                  ],
                                  "allowedDossierStatuses": ["OUVERT"],
                                  "observerUserIds": []
                                }
                                """.formatted(key, key)))
                .andExpect(status().isCreated());
    }

    private static MockMultipartFile bpmnFile(String key) {
        return new MockMultipartFile(
                "file",
                "diagram.bpmn20.xml",
                "application/xml",
                BPMN.formatted(key).getBytes(StandardCharsets.UTF_8));
    }

    private static RequestPostProcessor user(String subject, String... roles) {
        String[] effective = roles.length == 0 ? new String[] {"DOSSIER_MANAGER"} : roles;
        return oidcLogin().idToken(token -> token.subject(subject).claim("tenant_id", TENANT_ID))
                .authorities(Arrays.stream(effective)
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
