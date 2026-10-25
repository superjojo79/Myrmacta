package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.InvalidTemplateSyntaxException;
import be.myrmacta.domain.document.model.TemplateValidationException;
import be.myrmacta.domain.document.port.out.TemplateSyntaxChecker;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.infrastructure.config.DocumentConfig;
import be.myrmacta.infrastructure.config.DocumentPreviewProperties;
import be.myrmacta.infrastructure.mcp.McpActorScope;
import be.myrmacta.infrastructure.web.document.DocumentGenerationRateLimitExceededException;
import be.myrmacta.infrastructure.web.document.DocumentPreviewHtmlRateLimiter;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.document.port.in.CreateDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.ListDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.NewDocumentTemplateVersionUseCase;
import be.myrmacta.domain.document.port.in.PreviewDocumentUseCase;
import be.myrmacta.domain.document.port.in.UpdateDocumentTemplateUseCase;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

class DocumentTemplateToolsTest {

    private static final String TENANT = "tenant_dev";
    // Le limiteur compte par minute calendaire : une horloge figée évite qu'un test de quota
    // chevauchant une frontière de minute voie son compteur remis à zéro (pas de 429).
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:30Z"), ZoneOffset.UTC);

    private ListDocumentTemplateUseCase list;
    private CreateDocumentTemplateUseCase create;
    private UpdateDocumentTemplateUseCase update;
    private NewDocumentTemplateVersionUseCase newVersion;
    private TemplateSyntaxChecker checker;
    private DefineDocumentTemplateTool define;

    @BeforeEach
    void setUp() {
        list = mock(ListDocumentTemplateUseCase.class);
        create = mock(CreateDocumentTemplateUseCase.class);
        update = mock(UpdateDocumentTemplateUseCase.class);
        newVersion = mock(NewDocumentTemplateVersionUseCase.class);
        checker = mock(TemplateSyntaxChecker.class);
        define = new DefineDocumentTemplateTool(
                list, create, update, newVersion, new TenantProperties(TENANT), new McpAccessContextFactory(), checker);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "agent-1")
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static DocumentTemplate tpl(int version, TemplateStatus status, String variant) {
        return new DocumentTemplate(UUID.randomUUID(), "contrat", variant, null, "Contrat", version, status,
                "<p>x</p>", TENANT, Instant.EPOCH, Instant.EPOCH, "u");
    }

    private void existing(DocumentTemplate... templates) {
        when(list.list(eq(TENANT), eq("contrat"), eq(false), any()))
                .thenReturn(PageResult.of(List.of(templates), 0, 100, templates.length));
    }

    @Test
    void unknownKey_createsADraft() {
        existing();
        when(create.create(any())).thenReturn(tpl(1, TemplateStatus.DRAFT, null));

        var out = define.defineDocumentTemplate("contrat", null, null, "Contrat", "<p>new</p>", null);

        assertThat(out).containsEntry("operation", "create").containsEntry("dryRun", false);
        verify(update, never()).update(any());
        verify(newVersion, never()).newVersion(any(), any(), any());
    }

    @Test
    void latestDraft_isUpdatedInPlace_andTheBodyIsNeverReturned() {
        var draft = tpl(2, TemplateStatus.DRAFT, null);
        existing(tpl(1, TemplateStatus.ACTIVE, null), draft);
        when(update.update(any())).thenReturn(draft);

        var out = define.defineDocumentTemplate("contrat", null, null, null, "<p>v2</p>", null);

        assertThat(out).containsEntry("operation", "update");
        assertThat(out.toString()).doesNotContain("<p>");
        var captor = ArgumentCaptor.forClass(UpdateDocumentTemplateUseCase.UpdateCommand.class);
        verify(update).update(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(draft.id());
        assertThat(captor.getValue().updatedBy()).isEqualTo("agent-1");
    }

    @Test
    void latestActive_getsANewDraftVersion_andTheActiveVersionIsNeverUpdated() {
        var active = tpl(3, TemplateStatus.ACTIVE, null);
        var draft = tpl(4, TemplateStatus.DRAFT, null);
        existing(active);
        when(newVersion.newVersion(active.id(), TENANT, "agent-1")).thenReturn(draft);
        when(update.update(any())).thenReturn(draft);

        var out = define.defineDocumentTemplate("contrat", null, null, null, "<p>v4</p>", null);

        assertThat(out).containsEntry("operation", "newVersion");
        var captor = ArgumentCaptor.forClass(UpdateDocumentTemplateUseCase.UpdateCommand.class);
        verify(update).update(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(draft.id());
    }

    @Test
    void anArchivedLatest_createsAFreshDraft_andAnotherVariantIsIgnored() {
        existing(tpl(5, TemplateStatus.ARCHIVED, null), tpl(9, TemplateStatus.ACTIVE, "court"));
        when(create.create(any())).thenReturn(tpl(6, TemplateStatus.DRAFT, null));

        var out = define.defineDocumentTemplate("contrat", null, null, null, "<p>z</p>", null);

        assertThat(out).containsEntry("operation", "create");
        verify(newVersion, never()).newVersion(any(), any(), any());
    }

    @Test
    void dryRunWritesNothing_andBlankHtmlIsRefused() {
        existing(tpl(1, TemplateStatus.ACTIVE, null));

        var out = define.defineDocumentTemplate("contrat", null, null, null, "<p>d</p>", true);

        assertThat(out).containsEntry("operation", "newVersion").containsEntry("dryRun", true);
        verify(create, never()).create(any());
        verify(update, never()).update(any());
        verify(newVersion, never()).newVersion(any(), any(), any());
        assertThatThrownBy(() -> define.defineDocumentTemplate("contrat", null, null, null, "  ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PreviewDocumentTool previewTool(PreviewDocumentUseCase uc, int perMinute) {
        return new PreviewDocumentTool(uc, new McpAccessContextFactory(), new DocumentPreviewHtmlRateLimiter(FIXED_CLOCK),
                new DocumentPreviewProperties(perMinute),
                new be.myrmacta.infrastructure.config.DocumentGenerationProperties(1024, 30),
                tools.jackson.databind.json.JsonMapper.builder().build());
    }

    private static final AccessContext.User MANAGER = AccessContext.user("alice", java.util.Set.of("DOSSIER_MANAGER"));

    /** Revue adversariale LC4b (Moyen) : comme l'API REST, un acteur de session non administrateur (assistant) est refusé. */
    @Test
    void aNonAdminSessionActorCannotWriteTemplatesNorPreviewLive() {
        var uc = mock(PreviewDocumentUseCase.class);
        assertThatThrownBy(() -> McpActorScope.call(MANAGER,
                () -> define.defineDocumentTemplate("contrat", null, null, null, "<p>x</p>", null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> McpActorScope.call(MANAGER, () -> previewTool(uc, 30).previewDocument("<p>x</p>", null)))
                .isInstanceOf(AccessDeniedException.class);
        verify(create, never()).create(any());
        verify(uc, never()).previewHtml(any());
    }

    @Test
    void previewIsRateLimitedLikeTheRestEndpoint() {
        var uc = mock(PreviewDocumentUseCase.class);
        when(uc.previewHtml(any())).thenReturn("<p/>");
        var tool = previewTool(uc, 2);
        tool.previewDocument("<p>a</p>", null);
        tool.previewDocument("<p>a</p>", null);
        assertThatThrownBy(() -> tool.previewDocument("<p>a</p>", null))
                .isInstanceOf(DocumentGenerationRateLimitExceededException.class);
    }

    @Test
    void previewVariablesAreBounded() {
        var uc = mock(PreviewDocumentUseCase.class);
        assertThatThrownBy(() -> previewTool(uc, 30).previewDocument("<p>a</p>", Map.of("big", "x".repeat(2000))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(uc, never()).previewHtml(any());
    }

    @Test
    void anOversizedBodyIsRefusedBeforeAnyWrite_noOrphanDraftOnTheNewVersionBranch() {
        existing(tpl(3, TemplateStatus.ACTIVE, null));
        String huge = "x".repeat(DocumentConfig.MAX_HTML_BYTES + 1);
        assertThatThrownBy(() -> define.defineDocumentTemplate("contrat", null, null, null, huge, null))
                .isInstanceOf(TemplateValidationException.class);
        assertThatThrownBy(() -> define.defineDocumentTemplate("contrat", null, null, null, huge, true))
                .isInstanceOf(TemplateValidationException.class);
        verify(newVersion, never()).newVersion(any(), any(), any());
        verify(update, never()).update(any());
    }

    /** Revue adversariale LC4b (Moyen) : la liste est triée par date, pas par version — plus de 100 lignes ne doivent pas masquer le dernier brouillon. */
    @Test
    void theLatestVersionIsFoundAcrossAllPages() {
        var oldDraft = tpl(7, TemplateStatus.DRAFT, null);
        var recent = new java.util.ArrayList<DocumentTemplate>();
        for (int i = 0; i < 100; i++) {
            recent.add(tpl(1, TemplateStatus.ARCHIVED, "v" + i));
        }
        when(list.list(eq(TENANT), eq("contrat"), eq(false), any())).thenAnswer(inv -> {
            int page = ((be.myrmacta.domain.shared.PageQuery) inv.getArgument(3)).page();
            return page == 0
                    ? PageResult.of(recent, 0, 100, 101)
                    : PageResult.of(List.of(oldDraft), 1, 100, 101);
        });
        when(update.update(any())).thenReturn(oldDraft);

        var out = define.defineDocumentTemplate("contrat", null, null, null, "<p>z</p>", null);

        assertThat(out).containsEntry("operation", "update");
        verify(create, never()).create(any());
    }

    @Test
    void aSyntaxProblemIsReportedWithoutBlockingTheDraft_andTheLabelDefaultsToTheKey() {
        existing();
        org.mockito.Mockito.doThrow(new InvalidTemplateSyntaxException("Unescaped Thymeleaf interpolation"))
                .when(checker).check(any());
        when(create.create(any())).thenReturn(tpl(1, TemplateStatus.DRAFT, null));

        var out = define.defineDocumentTemplate("contrat", null, null, null, "<p th:utext=\"${a}\">x</p>", null);

        assertThat(out).containsEntry("syntax", "INVALID");
        assertThat(out.get("syntaxProblem").toString()).contains("Unescaped");
        var captor = ArgumentCaptor.forClass(CreateDocumentTemplateUseCase.CreateCommand.class);
        verify(create).create(captor.capture());
        assertThat(captor.getValue().label()).isEqualTo("contrat");
    }

    @Test
    void previewIsLiveOnly_neverByTemplateKey() {
        var uc = mock(PreviewDocumentUseCase.class);
        when(uc.previewHtml(any())).thenReturn("<p>Bonjour Ada</p>");
        var tool = previewTool(uc, 30);

        var out = tool.previewDocument("<p th:text=\"${nom}\"></p>", Map.of("nom", "Ada"));

        assertThat(out).containsEntry("html", "<p>Bonjour Ada</p>");
        var captor = ArgumentCaptor.forClass(PreviewDocumentUseCase.PreviewCommand.class);
        verify(uc).previewHtml(captor.capture());
        assertThat(captor.getValue().isLive()).isTrue();
        assertThat(captor.getValue().templateKey()).isNull();
        assertThatThrownBy(() -> tool.previewDocument(" ", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
