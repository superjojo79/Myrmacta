package com.flowforge.infrastructure.web.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.IncidentNotRetryableException;
import com.flowforge.domain.workflow.model.IncidentState;
import com.flowforge.domain.workflow.model.TenantIncident;
import com.flowforge.domain.workflow.model.WorkflowIncident;
import com.flowforge.domain.workflow.model.WorkflowIncidentNotFoundException;
import com.flowforge.domain.workflow.port.in.WorkflowIncidentsUseCase;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** TODO §94 — contrat HTTP des incidents : liste du lien, rejeu (codes, audit), liste du tenant. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class WorkflowIncidentApiTest {

    private static final UUID LINK = UUID.randomUUID();
    private static final WorkflowIncident INCIDENT = new WorkflowIncident("job-1", "pi-1", "mailRefus",
            "E-mail : refus", IncidentState.INCIDENT, Instant.parse("2026-10-18T18:51:26Z"), 0,
            "notificationTo source dossierField:email is not a single e-mail address");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkflowIncidentsUseCase useCase;

    private static RequestPostProcessor user(String id, String role) {
        return oidcLogin()
                .idToken(t -> t.subject(id).claim("tenant_id", "tenant_dev").claim("email", id + "@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void theLinkIncidentsCarryTheMessageOnlyWhenTheUseCaseKeptIt() throws Exception {
        when(useCase.listForLink(eq(LINK), any())).thenReturn(List.of(INCIDENT, INCIDENT.withoutMessage()));

        mockMvc.perform(get("/api/workflow-links/{id}/incidents", LINK).with(user("admin", "WORKFLOW_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value("job-1"))
                .andExpect(jsonPath("$[0].activityId").value("mailRefus"))
                .andExpect(jsonPath("$[0].activityName").value("E-mail : refus"))
                .andExpect(jsonPath("$[0].state").value("INCIDENT"))
                .andExpect(jsonPath("$[0].retriesLeft").value(0))
                .andExpect(jsonPath("$[0].since").value("2026-10-18T18:51:26Z"))
                .andExpect(jsonPath("$[0].message").exists())
                .andExpect(jsonPath("$[1].message").doesNotExist())
                .andExpect(jsonPath("$[0].processInstanceId").doesNotExist());
    }

    @Test
    void retryAnswers204AndIsAuditedWithoutTheMessage(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/workflow-links/{id}/incidents/{job}/retry", LINK, "job-1")
                        .with(user("admin", "WORKFLOW_ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());

        verify(useCase).retry(eq(LINK), eq("job-1"), any());
        assertThat(output).contains("event=incident_retried").contains("linkId=" + LINK).contains("jobId=job-1")
                .doesNotContain("single e-mail address");
    }

    @Test
    void retryMapsDeniedUnknownAndStillRetryingTo403404And409() throws Exception {
        doThrow(new AccessDeniedException("no")).when(useCase).retry(eq(LINK), eq("denied"), any());
        doThrow(new WorkflowIncidentNotFoundException("no")).when(useCase).retry(eq(LINK), eq("absent"), any());
        doThrow(new IncidentNotRetryableException("busy")).when(useCase).retry(eq(LINK), eq("busy"), any());

        mockMvc.perform(post("/api/workflow-links/{id}/incidents/{job}/retry", LINK, "denied")
                .with(user("m1", "DOSSIER_MANAGER")).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/workflow-links/{id}/incidents/{job}/retry", LINK, "absent")
                .with(user("admin", "WORKFLOW_ADMIN")).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/workflow-links/{id}/incidents/{job}/retry", LINK, "busy")
                .with(user("admin", "WORKFLOW_ADMIN")).with(csrf())).andExpect(status().isConflict());
    }

    @Test
    void theTenantListIsPaginatedWithTheDossierAndTheTruncationFlag() throws Exception {
        UUID dossier = UUID.randomUUID();
        TenantIncident row = new TenantIncident(INCIDENT, LINK, dossier, "DOS-7", "autorisationRiverain");
        when(useCase.listForTenant(any(), eq(new PageQuery(1, 10)))).thenReturn(
                new WorkflowIncidentsUseCase.TenantIncidentPage(new PageResult<>(List.of(row), 1, 10, 11, 2), true));

        mockMvc.perform(get("/api/admin/incidents").param("page", "1").param("size", "10")
                        .with(user("admin", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].jobId").value("job-1"))
                .andExpect(jsonPath("$.content[0].linkId").value(LINK.toString()))
                .andExpect(jsonPath("$.content[0].dossierId").value(dossier.toString()))
                .andExpect(jsonPath("$.content[0].dossierReference").value("DOS-7"))
                .andExpect(jsonPath("$.content[0].processDefinitionKey").value("autorisationRiverain"))
                .andExpect(jsonPath("$.content[0].message").exists())
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void theTenantListIsRefusedToNonAdmins() throws Exception {
        when(useCase.listForTenant(any(), any())).thenThrow(new AccessDeniedException("admins only"));

        mockMvc.perform(get("/api/admin/incidents").with(user("m1", "DOSSIER_MANAGER")))
                .andExpect(status().isForbidden());
    }
}
