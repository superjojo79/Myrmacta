package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierElementView;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AddDossierElementToolTest {

    private static final String TENANT = "tenant_dev";

    private AddDossierElementUseCase addDossierElementUseCase;
    private GetDossierUseCase getDossierUseCase;
    private DossierAccessPolicy accessPolicy;
    private AddDossierElementTool tool;
    private UUID dossierId;

    @BeforeEach
    void setUp() {
        addDossierElementUseCase = mock(AddDossierElementUseCase.class);
        getDossierUseCase = mock(GetDossierUseCase.class);
        accessPolicy = mock(DossierAccessPolicy.class);
        tool = new AddDossierElementTool(
                addDossierElementUseCase, getDossierUseCase, accessPolicy, new McpAccessContextFactory());
        dossierId = UUID.randomUUID();
        when(getDossierUseCase.get(eq(dossierId), any())).thenReturn(dossier(dossierId));
        bindJwt();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addNote_delegatesCommand() {
        DossierElement saved = element(ElementType.NOTE);
        when(addDossierElementUseCase.add(any(), any())).thenReturn(saved);

        Object result = tool.addDossierElement(
                dossierId.toString(), "NOTE", "Memo", Map.of("text", "hello"), false);

        ArgumentCaptor<AddDossierElementUseCase.AddCommand> captor =
                ArgumentCaptor.forClass(AddDossierElementUseCase.AddCommand.class);
        verify(addDossierElementUseCase).add(captor.capture(), any());
        verify(accessPolicy).assertCanEdit(any(), any());
        assertThat(captor.getValue().type()).isEqualTo(ElementType.NOTE);
        assertThat(captor.getValue().label()).isEqualTo("Memo");
        assertThat(captor.getValue().metadata()).containsEntry("text", "hello");
        assertThat(captor.getValue().storageRef()).isNull();
        assertThat(captor.getValue().addedBy()).isEqualTo("agent-1");
        assertThat(result).isInstanceOf(DossierElementView.class);
    }

    @Test
    void producedDoc_isRejectedWithoutCallingAdd() {
        assertThatThrownBy(() -> tool.addDossierElement(
                        dossierId.toString(), "PRODUCED_DOC", "Doc", Map.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dedicated endpoint");
        verify(addDossierElementUseCase, never()).add(any(), any());
        verify(getDossierUseCase, never()).get(any(), any());
    }

    @Test
    void attachment_isRejectedWithoutCallingAdd() {
        assertThatThrownBy(() -> tool.addDossierElement(
                        dossierId.toString(), "ATTACHMENT", "File", Map.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dedicated endpoint");
        verify(addDossierElementUseCase, never()).add(any(), any());
        verify(getDossierUseCase, never()).get(any(), any());
    }

    @Test
    void unknownType_isRejected() {
        assertThatThrownBy(() -> tool.addDossierElement(dossierId.toString(), "WIDGET", "x", Map.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("type must be NOTE, LINK or INFORMATION");
    }

    @Test
    void dryRun_doesNotAdd() {
        @SuppressWarnings("unchecked")
        Map<String, Object> preview = (Map<String, Object>)
                tool.addDossierElement(dossierId.toString(), "NOTE", "Memo", Map.of("text", "hello"), true);

        assertThat(preview).containsEntry("dryRun", true).containsEntry("type", "NOTE");
        verify(addDossierElementUseCase, never()).add(any(), any());
        verify(accessPolicy).assertCanEdit(any(), any());
    }

    private static void bindJwt() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("sub", "agent-1")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static Dossier dossier(UUID id) {
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        return new Dossier(
                id, TENANT, "REF-1", "SINISTRE", "Acme", DossierStatus.OUVERT,
                Map.of(), null, 0L, "creator", now, "creator", now, null, null, null);
    }

    private DossierElement element(ElementType type) {
        return new DossierElement(
                UUID.randomUUID(),
                dossierId,
                TENANT,
                type,
                "Memo",
                null,
                Map.of("text", "hello"),
                "agent-1",
                Instant.parse("2026-09-08T00:00:00Z"),
                null);
    }
}
