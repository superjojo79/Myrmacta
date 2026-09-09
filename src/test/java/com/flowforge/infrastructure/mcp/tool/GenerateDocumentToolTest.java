package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.port.in.GenerateDocumentUseCase;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierElementView;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.infrastructure.config.DocumentGenerationProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import com.flowforge.infrastructure.web.document.DocumentGenerationRateLimitExceededException;
import com.flowforge.infrastructure.web.document.DocumentGenerationRateLimiter;
import java.time.Clock;
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

class GenerateDocumentToolTest {

    private static final String TENANT = "tenant_dev";

    private GetDossierUseCase getDossierUseCase;
    private GetDossierTypeUseCase getDossierTypeUseCase;
    private DossierAccessPolicy accessPolicy;
    private GenerateDocumentUseCase generateDocumentUseCase;
    private DocumentStoragePort documentStoragePort;
    private AddDossierElementUseCase addDossierElementUseCase;
    private GenerateDocumentTool tool;
    private UUID dossierId;

    @BeforeEach
    void setUp() {
        getDossierUseCase = mock(GetDossierUseCase.class);
        getDossierTypeUseCase = mock(GetDossierTypeUseCase.class);
        accessPolicy = mock(DossierAccessPolicy.class);
        generateDocumentUseCase = mock(GenerateDocumentUseCase.class);
        documentStoragePort = mock(DocumentStoragePort.class);
        addDossierElementUseCase = mock(AddDossierElementUseCase.class);
        tool = newTool(new DocumentGenerationRateLimiter(Clock.systemUTC()), 30);
        dossierId = UUID.randomUUID();
        when(getDossierUseCase.get(eq(dossierId), any())).thenReturn(dossier(dossierId));
        when(getDossierTypeUseCase.getByCode(eq(TENANT), eq("SINISTRE"), any()))
                .thenReturn(type(List.of("quote")));
        bindJwt();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generate_storesAndAttachesProducedDoc() {
        when(generateDocumentUseCase.generate(any())).thenReturn(new byte[] {1, 2, 3});
        when(documentStoragePort.store(eq(TENANT), eq("application/pdf"), any())).thenReturn("tenant_dev/abc.pdf");
        DossierElement saved = producedDoc("tenant_dev/abc.pdf");
        when(addDossierElementUseCase.add(any(), any())).thenReturn(saved);

        Object result = tool.generateDocument(
                dossierId.toString(), "quote", "v1", "fr", Map.of("extra", "x"), false);

        ArgumentCaptor<GenerateDocumentUseCase.GenerateDocumentCommand> gen =
                ArgumentCaptor.forClass(GenerateDocumentUseCase.GenerateDocumentCommand.class);
        verify(generateDocumentUseCase).generate(gen.capture());
        assertThat(gen.getValue().templateKey()).isEqualTo("quote");
        assertThat(gen.getValue().variantKey()).isEqualTo("v1");
        assertThat(gen.getValue().locale()).isEqualTo("fr");
        assertThat(gen.getValue().variables()).containsEntry("amount", 10).containsEntry("extra", "x");

        ArgumentCaptor<AddDossierElementUseCase.AddCommand> add =
                ArgumentCaptor.forClass(AddDossierElementUseCase.AddCommand.class);
        verify(addDossierElementUseCase).add(add.capture(), any());
        assertThat(add.getValue().type()).isEqualTo(ElementType.PRODUCED_DOC);
        assertThat(add.getValue().label()).isEqualTo("Document généré — quote");
        assertThat(add.getValue().storageRef()).isEqualTo("tenant_dev/abc.pdf");
        assertThat(add.getValue().addedBy()).isEqualTo("agent-1");
        assertThat(add.getValue().metadata()).containsEntry("templateKey", "quote");

        assertThat(result).isInstanceOf(DossierElementView.class);
        DossierElementView view = (DossierElementView) result;
        assertThat(view.downloadable()).isTrue();
        assertThat(view.label()).isEqualTo("Document généré — quote");
        assertThat(view.toString()).doesNotContain("storageRef").doesNotContain("tenant_dev/abc.pdf");
    }

    @Test
    void generate_withoutVariantOrLocale_omitsThoseMetadataKeys() {
        when(generateDocumentUseCase.generate(any())).thenReturn(new byte[] {1});
        when(documentStoragePort.store(eq(TENANT), eq("application/pdf"), any())).thenReturn("ref");
        when(addDossierElementUseCase.add(any(), any())).thenReturn(producedDoc("ref"));

        tool.generateDocument(dossierId.toString(), "quote", null, null, Map.of(), false);

        ArgumentCaptor<AddDossierElementUseCase.AddCommand> add =
                ArgumentCaptor.forClass(AddDossierElementUseCase.AddCommand.class);
        verify(addDossierElementUseCase).add(add.capture(), any());
        assertThat(add.getValue().metadata())
                .containsEntry("templateKey", "quote")
                .containsEntry("contentType", "application/pdf")
                .doesNotContainKeys("variantKey", "locale");
    }

    @Test
    void generate_blankVariantAndLocale_treatedAsAbsent() {
        when(generateDocumentUseCase.generate(any())).thenReturn(new byte[] {1});
        when(documentStoragePort.store(eq(TENANT), eq("application/pdf"), any())).thenReturn("ref");
        when(addDossierElementUseCase.add(any(), any())).thenReturn(producedDoc("ref"));

        tool.generateDocument(dossierId.toString(), "quote", "  ", "   ", Map.of(), false);

        ArgumentCaptor<GenerateDocumentUseCase.GenerateDocumentCommand> gen =
                ArgumentCaptor.forClass(GenerateDocumentUseCase.GenerateDocumentCommand.class);
        verify(generateDocumentUseCase).generate(gen.capture());
        assertThat(gen.getValue().variantKey()).isNull();
        assertThat(gen.getValue().locale()).isNull();
    }

    @Test
    void templateNotListed_isDeniedWithoutGenerating() {
        when(getDossierTypeUseCase.getByCode(eq(TENANT), eq("SINISTRE"), any()))
                .thenReturn(type(List.of("other")));

        assertThatThrownBy(() ->
                        tool.generateDocument(dossierId.toString(), "quote", null, null, Map.of(), false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not list this document template");
        verify(generateDocumentUseCase, never()).generate(any());
        verify(documentStoragePort, never()).store(any(), any(), any());
        verify(addDossierElementUseCase, never()).add(any(), any());
    }

    @Test
    void reservedOverlay_isRejected() {
        assertThatThrownBy(() -> tool.generateDocument(
                        dossierId.toString(), "quote", null, null, Map.of("tenantId", "x"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        verify(generateDocumentUseCase, never()).generate(any());
    }

    @Test
    void rateLimitExceeded_deniesGeneration() {
        DocumentGenerationRateLimiter exhausted = new DocumentGenerationRateLimiter(Clock.systemUTC());
        exhausted.tryConsume("agent-1", 1);
        tool = newTool(exhausted, 1);

        assertThatThrownBy(() ->
                        tool.generateDocument(dossierId.toString(), "quote", null, null, Map.of(), false))
                .isInstanceOf(DocumentGenerationRateLimitExceededException.class);
        verify(generateDocumentUseCase, never()).generate(any());
        verify(documentStoragePort, never()).store(any(), any(), any());
        verify(addDossierElementUseCase, never()).add(any(), any());
    }

    @Test
    void dryRun_doesNotConsumeRateLimit() {
        DocumentGenerationRateLimiter exhausted = new DocumentGenerationRateLimiter(Clock.systemUTC());
        exhausted.tryConsume("agent-1", 1);
        tool = newTool(exhausted, 1);

        tool.generateDocument(dossierId.toString(), "quote", null, null, Map.of(), true);

        verify(generateDocumentUseCase, never()).generate(any());
    }

    @Test
    void dryRun_doesNotGenerate() {
        @SuppressWarnings("unchecked")
        Map<String, Object> preview = (Map<String, Object>)
                tool.generateDocument(dossierId.toString(), "quote", null, null, Map.of(), true);

        assertThat(preview).containsEntry("dryRun", true).containsEntry("templateKey", "quote");
        verify(generateDocumentUseCase, never()).generate(any());
        verify(documentStoragePort, never()).store(any(), any(), any());
        verify(addDossierElementUseCase, never()).add(any(), any());
    }

    private GenerateDocumentTool newTool(DocumentGenerationRateLimiter rateLimiter, int rateLimitPerMinute) {
        return new GenerateDocumentTool(
                getDossierUseCase,
                getDossierTypeUseCase,
                accessPolicy,
                generateDocumentUseCase,
                documentStoragePort,
                addDossierElementUseCase,
                new TenantProperties(TENANT),
                new McpAccessContextFactory(),
                rateLimiter,
                new DocumentGenerationProperties(0, rateLimitPerMinute));
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
                Map.of("amount", 10), null, 0L, "creator", now, "creator", now, null, null, null);
    }

    private static DossierType type(List<String> templates) {
        return new DossierType(
                UUID.randomUUID(),
                TENANT,
                "SINISTRE",
                "Sinistre",
                "wf-sinistre",
                true,
                false,
                List.of(),
                null,
                false,
                null,
                templates,
                "system",
                "system");
    }

    private DossierElement producedDoc(String storageRef) {
        return new DossierElement(
                UUID.randomUUID(),
                dossierId,
                TENANT,
                ElementType.PRODUCED_DOC,
                "Document généré — quote",
                storageRef,
                Map.of("templateKey", "quote", "contentType", "application/pdf"),
                "agent-1",
                Instant.parse("2026-09-08T00:00:00Z"),
                null);
    }
}
