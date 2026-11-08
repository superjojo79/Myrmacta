package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.ListNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.NewNotificationTemplateVersionUseCase;
import be.myrmacta.domain.notification.template.port.in.PreviewNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.UpdateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateChecker;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.config.NotificationTemplateProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import be.myrmacta.infrastructure.mcp.McpActorScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** TODO §123 (CS7) — outils MCP des gabarits de notification : brouillon seulement, dryRun, garde, aucun corps renvoyé. */
class NotificationTemplateToolsTest {

    private static final String SUBJECT = "Dossier [[${dossier.reference}]]";
    private static final String TEXT = "Texte secret du gabarit";
    private static final String HTML = "<p>Html secret du gabarit</p>";

    private ListNotificationTemplateUseCase list;
    private CreateNotificationTemplateUseCase create;
    private UpdateNotificationTemplateUseCase update;
    private NewNotificationTemplateVersionUseCase newVersion;
    private PreviewNotificationTemplateUseCase preview;
    private NotificationTemplateChecker checker;
    private DefineNotificationTemplateTool define;
    private PreviewNotificationTemplateTool previewTool;
    private final NotificationTemplateProperties properties = NotificationTemplateProperties.defaults();

    @BeforeEach
    void setUp() {
        list = mock(ListNotificationTemplateUseCase.class);
        create = mock(CreateNotificationTemplateUseCase.class);
        update = mock(UpdateNotificationTemplateUseCase.class);
        newVersion = mock(NewNotificationTemplateVersionUseCase.class);
        preview = mock(PreviewNotificationTemplateUseCase.class);
        checker = mock(NotificationTemplateChecker.class);
        McpAccessContextFactory actors = new McpAccessContextFactory();
        define = new DefineNotificationTemplateTool(list, create, update, newVersion, checker, properties, actors);
        previewTool = new PreviewNotificationTemplateTool(
                preview, list, properties, actors, new tools.jackson.databind.ObjectMapper());
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "agent-1")
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static NotificationTemplate tpl(int version, TemplateStatus status, String variant) {
        return new NotificationTemplate(UUID.randomUUID(), "accuse", variant, null, "Accusé", version, status, "Objet", "", null,
                "tenant_dev", Instant.EPOCH, Instant.EPOCH, "u");
    }

    private void existing(NotificationTemplate... templates) {
        when(list.list(eq("accuse"), any())).thenReturn(PageResult.of(List.of(templates), 0, 100, templates.length));
    }

    // ---- define ----

    @Test
    void unknownKey_createsADraft_afterCheckingTheTemplate() {
        existing();
        when(create.create(any())).thenReturn(tpl(1, TemplateStatus.DRAFT, null));

        var out = define.defineNotificationTemplate("accuse", null, "fr", "Accusé", SUBJECT, TEXT, HTML, null);

        assertThat(out).containsEntry("operation", "create").containsEntry("dryRun", false).containsEntry("syntax", "OK");
        var captured = ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(checker).check(captured.capture());
        assertThat(captured.getValue().subject()).isEqualTo(SUBJECT);
        assertThat(captured.getValue().htmlBody()).isEqualTo(HTML);
        var command = ArgumentCaptor.forClass(CreateNotificationTemplateUseCase.CreateCommand.class);
        verify(create).create(command.capture());
        assertThat(command.getValue().templateKey()).isEqualTo("accuse");
        assertThat(command.getValue().locale()).isEqualTo("fr");
        assertThat(command.getValue().label()).isEqualTo("Accusé");
        assertThat(command.getValue().updatedBy()).isEqualTo("agent-1");
        verify(update, never()).update(any());
        verify(newVersion, never()).newVersion(any(), any());
    }

    @Test
    void latestDraft_isReplacedInPlace_andNoBodyIsEverReturned() {
        var draft = tpl(2, TemplateStatus.DRAFT, null);
        existing(tpl(1, TemplateStatus.ACTIVE, null), draft);
        when(update.update(any())).thenReturn(draft);

        var out = define.defineNotificationTemplate("accuse", null, null, null, SUBJECT, TEXT, null, null);

        assertThat(out).containsEntry("operation", "update");
        assertThat(out.toString()).doesNotContain("secret").doesNotContain("Objet").doesNotContain("<p>");
        var captor = ArgumentCaptor.forClass(UpdateNotificationTemplateUseCase.UpdateCommand.class);
        verify(update).update(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(draft.id());
        assertThat(captor.getValue().updatedBy()).isEqualTo("agent-1");
        // l'appel décrit le gabarit entier : sans htmlBody, le corps HTML du brouillon est supprimé
        assertThat(captor.getValue().htmlBody()).isEmpty();
    }

    @Test
    void latestActive_getsANewDraftVersion_andTheActiveVersionIsNeverUpdated() {
        var active = tpl(3, TemplateStatus.ACTIVE, null);
        var draft = tpl(4, TemplateStatus.DRAFT, null);
        existing(active);
        when(newVersion.newVersion(active.id(), "agent-1")).thenReturn(draft);
        when(update.update(any())).thenReturn(draft);

        var out = define.defineNotificationTemplate("accuse", null, null, null, SUBJECT, TEXT, HTML, null);

        assertThat(out).containsEntry("operation", "newVersion");
        var captor = ArgumentCaptor.forClass(UpdateNotificationTemplateUseCase.UpdateCommand.class);
        verify(update).update(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(draft.id());
        verify(create, never()).create(any());
    }

    @Test
    void anArchivedLatest_createsAFreshDraft_andAnotherVariantIsIgnored() {
        existing(tpl(5, TemplateStatus.ARCHIVED, null), tpl(9, TemplateStatus.ACTIVE, "court"));
        when(create.create(any())).thenReturn(tpl(6, TemplateStatus.DRAFT, null));

        var out = define.defineNotificationTemplate("accuse", null, null, null, SUBJECT, TEXT, null, null);

        assertThat(out).containsEntry("operation", "create");
        verify(newVersion, never()).newVersion(any(), any());
    }

    @Test
    void aTemplateRefusedByTheChecker_isNotWritten_andTheErrorNamesThePartAndTheRule() {
        existing();
        doThrow(new NotificationTemplateValidationException("htmlBody", "<img> is not allowed"))
                .when(checker).check(any());

        assertThatThrownBy(() -> define.defineNotificationTemplate("accuse", null, null, null, SUBJECT, TEXT, "<img src=x>", null))
                .isInstanceOf(NotificationTemplateValidationException.class)
                .hasMessageContaining("htmlBody")
                .hasMessageContaining("<img> is not allowed");

        verifyNoInteractions(create, update, newVersion);
    }

    @Test
    void aBodyOverTheLimit_isRefusedBeforeAnyWrite() {
        existing();
        String huge = "x".repeat(properties.textBodyMaxBytes() + 1);

        assertThatThrownBy(() -> define.defineNotificationTemplate("accuse", null, null, null, SUBJECT, huge, null, null))
                .isInstanceOf(NotificationTemplateValidationException.class)
                .hasMessageContaining("textBody");

        verifyNoInteractions(create, update, newVersion);
    }

    @Test
    void dryRunWritesNothing_andReportsARefusedTemplateWithoutRaising() {
        existing(tpl(1, TemplateStatus.ACTIVE, null));
        doThrow(new NotificationTemplateValidationException("htmlBody", "<style> is not allowed"))
                .when(checker).check(any());

        var out = define.defineNotificationTemplate("accuse", null, null, null, SUBJECT, TEXT, "<style/>", true);

        assertThat(out).containsEntry("operation", "newVersion").containsEntry("dryRun", true).containsEntry("syntax", "INVALID");
        assertThat(out.get("syntaxProblem").toString()).contains("<style> is not allowed");
        assertThat(out).containsKey("currentVersion");
        verifyNoInteractions(create, update, newVersion);
    }

    @Test
    void blankSubjectBodyOrKey_andAnInvalidKey_areRefused() {
        assertThatThrownBy(() -> define.defineNotificationTemplate(" ", null, null, null, SUBJECT, TEXT, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> define.defineNotificationTemplate("Pas Valide", null, null, null, SUBJECT, TEXT, null, null))
                .isInstanceOf(NotificationTemplateValidationException.class)
                .hasMessageContaining("templateKey");
        assertThatThrownBy(() -> define.defineNotificationTemplate("accuse", null, null, null, " ", TEXT, null, null))
                .isInstanceOf(NotificationTemplateValidationException.class)
                .hasMessageContaining("subject");
        assertThatThrownBy(() -> define.defineNotificationTemplate("accuse", null, null, null, SUBJECT, "", null, null))
                .isInstanceOf(NotificationTemplateValidationException.class)
                .hasMessageContaining("textBody");
        verifyNoInteractions(create, update, newVersion);
    }

    @Test
    void anActorThatIsNeitherTenantAdminNorApiService_isRefusedByEveryTool_andNothingIsCalled() {
        var manager = AccessContext.user("bob", Set.of("DOSSIER_MANAGER"));

        McpActorScope.run(manager, () -> {
            assertThatThrownBy(() -> define.defineNotificationTemplate("accuse", null, null, null, SUBJECT, TEXT, null, null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> previewTool.previewNotificationTemplate(null, null, null, SUBJECT, TEXT, null, null))
                    .isInstanceOf(AccessDeniedException.class);
        });

        verifyNoInteractions(list, create, update, newVersion, preview, checker);
    }

    @Test
    void aTenantAdminOfTheSession_mayDefineAndPreview() {
        existing();
        when(create.create(any())).thenReturn(tpl(1, TemplateStatus.DRAFT, null));
        when(preview.preview(any())).thenReturn(new RenderedNotification("S", "T", null));
        var admin = AccessContext.user("alice", Set.of("TENANT_ADMIN"));

        McpActorScope.run(admin, () -> {
            assertThat(define.defineNotificationTemplate("accuse", null, null, null, SUBJECT, TEXT, null, null))
                    .containsEntry("operation", "create");
            assertThat(previewTool.previewNotificationTemplate(null, null, null, SUBJECT, TEXT, null, null))
                    .containsEntry("subject", "S");
        });

        var command = ArgumentCaptor.forClass(CreateNotificationTemplateUseCase.CreateCommand.class);
        verify(create).create(command.capture());
        assertThat(command.getValue().updatedBy()).isEqualTo("alice");
    }

    // ---- list ----

    @Test
    void list_returnsMetadataOnly_withDefaultsForThePage() {
        var t = new NotificationTemplate(UUID.randomUUID(), "accuse", "court", "fr", "Accusé", 2, TemplateStatus.ACTIVE,
                "Objet confidentiel", "", null, "tenant_dev", Instant.EPOCH, Instant.EPOCH, "alice");
        when(list.list(eq("accuse"), eq(PageQuery.of(0, 20)))).thenReturn(PageResult.of(List.of(t), 0, 20, 1));

        var result = new ListNotificationTemplatesTool(list).listNotificationTemplates("accuse", null, null);

        assertThat(result.totalElements()).isEqualTo(1);
        var summary = result.content().getFirst();
        assertThat(summary.templateKey()).isEqualTo("accuse");
        assertThat(summary.variantKey()).isEqualTo("court");
        assertThat(summary.locale()).isEqualTo("fr");
        assertThat(summary.version()).isEqualTo(2);
        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(result.toString()).doesNotContain("confidentiel");
    }

    @Test
    void list_refusesAPageSizeOverTheLimit() {
        assertThatThrownBy(() -> new ListNotificationTemplatesTool(list).listNotificationTemplates(null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(list);
    }

    // ---- preview ----

    @Test
    void preview_ofLiveContent_rendersWithTheGivenModel() {
        when(preview.preview(any())).thenReturn(new RenderedNotification("Dossier D-1", "Bonjour", "<p>Bonjour</p>"));
        Map<String, Object> model = Map.of("dossier", Map.of("reference", "D-1"));

        var out = previewTool.previewNotificationTemplate(null, null, null, SUBJECT, TEXT, HTML, model);

        assertThat(out).containsEntry("subject", "Dossier D-1").containsEntry("textBody", "Bonjour")
                .containsEntry("htmlBody", "<p>Bonjour</p>");
        var captor = ArgumentCaptor.forClass(PreviewNotificationTemplateUseCase.PreviewCommand.class);
        verify(preview).preview(captor.capture());
        assertThat(captor.getValue().isLive()).isTrue();
        assertThat(captor.getValue().subject()).isEqualTo(SUBJECT);
        assertThat(captor.getValue().model()).isEqualTo(model);
    }

    @Test
    void preview_byKey_rendersTheLatestVersionDraftIncluded() {
        var draft = tpl(2, TemplateStatus.DRAFT, null);
        existing(tpl(1, TemplateStatus.ACTIVE, null), draft);
        when(preview.preview(any())).thenReturn(new RenderedNotification("S", "T", null));

        var out = previewTool.previewNotificationTemplate("accuse", null, null, null, null, null, null);

        assertThat(out).containsEntry("htmlBody", null);
        var captor = ArgumentCaptor.forClass(PreviewNotificationTemplateUseCase.PreviewCommand.class);
        verify(preview).preview(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(draft.id());
        assertThat(captor.getValue().model()).isEmpty();
    }

    @Test
    void preview_byAnUnknownKey_orWithBothKeyAndContent_orWithNothing_isRefused() {
        existing();

        assertThatThrownBy(() -> previewTool.previewNotificationTemplate("accuse", null, null, null, null, null, null))
                .isInstanceOf(NotificationTemplateValidationException.class)
                .hasMessageContaining("accuse");
        assertThatThrownBy(() -> previewTool.previewNotificationTemplate("accuse", null, null, SUBJECT, TEXT, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either");
        assertThatThrownBy(() -> previewTool.previewNotificationTemplate(null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(preview);
    }

    @Test
    void preview_withAModelOverTheLimit_isRefusedBeforeRendering() {
        Map<String, Object> model = Map.of("variables", Map.of("blob", "x".repeat(properties.previewModelMaxBytes() + 1)));

        assertThatThrownBy(() -> previewTool.previewNotificationTemplate(null, null, null, SUBJECT, TEXT, null, model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model exceeds");
        verifyNoInteractions(preview);
    }
}
