package com.flowforge.domain.document.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.InvalidTemplateStateException;
import com.flowforge.domain.document.model.InvalidTemplateSyntaxException;
import com.flowforge.domain.document.model.TemplateNotFoundException;
import com.flowforge.domain.document.model.TemplateResolutionRequest;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.model.TemplateValidationException;
import com.flowforge.domain.document.port.in.CreateDocumentTemplateUseCase;
import com.flowforge.domain.document.port.in.UpdateDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import com.flowforge.domain.document.port.out.TemplateSyntaxChecker;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentTemplateUseCasesTest {

    private static final String TENANT = "tenant_dev";
    private static final int MAX_HTML = 5 * 1024 * 1024;

    @Mock
    private DocumentTemplateRepository repository;

    @Mock
    private TemplateSyntaxChecker syntaxChecker;

    private CreateDocumentTemplateUseCaseImpl create;
    private UpdateDocumentTemplateUseCaseImpl update;
    private ActivateDocumentTemplateUseCaseImpl activate;
    private ArchiveDocumentTemplateUseCaseImpl archive;
    private NewDocumentTemplateVersionUseCaseImpl newVersion;
    private ListDocumentTemplateUseCaseImpl list;
    private GetDocumentTemplateUseCaseImpl get;
    private ResolveDocumentTemplateUseCaseImpl resolve;

    @BeforeEach
    void setUp() {
        create = new CreateDocumentTemplateUseCaseImpl(TENANT, repository, MAX_HTML);
        update = new UpdateDocumentTemplateUseCaseImpl(TENANT, repository, MAX_HTML);
        activate = new ActivateDocumentTemplateUseCaseImpl(TENANT, repository, syntaxChecker);
        archive = new ArchiveDocumentTemplateUseCaseImpl(TENANT, repository);
        newVersion = new NewDocumentTemplateVersionUseCaseImpl(TENANT, repository);
        list = new ListDocumentTemplateUseCaseImpl(TENANT, repository);
        get = new GetDocumentTemplateUseCaseImpl(TENANT, repository);
        resolve = new ResolveDocumentTemplateUseCaseImpl(TENANT, repository);
    }

    @Test
    void create_normalizesBlankVariantLocale_andAssignsNextVersion() {
        when(repository.nextVersion(TENANT, "contrat", null, null)).thenReturn(1);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var created = create.create(new CreateDocumentTemplateUseCase.CreateCommand(
                "contrat", "  ", "", "label", "<p>ok</p>", "admin"));

        assertThat(created.status()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.variantKey()).isNull();
        assertThat(created.locale()).isNull();
    }

    @Test
    void create_rejectsBlankKeyOrHtmlOrOversizedHtml() {
        assertThatThrownBy(() -> create.create(new CreateDocumentTemplateUseCase.CreateCommand(
                        " ", null, null, "l", "<p>ok</p>", "a")))
                .isInstanceOf(TemplateValidationException.class);
        assertThatThrownBy(() -> create.create(new CreateDocumentTemplateUseCase.CreateCommand(
                        "k", null, null, "l", "  ", "a")))
                .isInstanceOf(TemplateValidationException.class);
        assertThatThrownBy(() -> create.create(new CreateDocumentTemplateUseCase.CreateCommand(
                        "k", null, null, "l", "x".repeat(MAX_HTML + 1), "a")))
                .isInstanceOf(TemplateValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_allowsDraftOnly() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT))
                .thenReturn(Optional.of(sample(id, TemplateStatus.DRAFT, 1)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var updated = update.update(new UpdateDocumentTemplateUseCase.UpdateCommand(
                id, "new", "<p>v2</p>", "admin"));
        assertThat(updated.label()).isEqualTo("new");
        assertThat(updated.htmlContent()).isEqualTo("<p>v2</p>");

        when(repository.findByIdAndTenantId(id, TENANT))
                .thenReturn(Optional.of(sample(id, TemplateStatus.ACTIVE, 1)));
        assertThatThrownBy(() -> update.update(new UpdateDocumentTemplateUseCase.UpdateCommand(
                        id, "x", "<p>y</p>", "admin")))
                .isInstanceOf(InvalidTemplateStateException.class);
    }

    @Test
    void activate_checksSyntaxThenArchivesPreviousActive() {
        UUID draftId = UUID.randomUUID();
        UUID previousId = UUID.randomUUID();
        var draft = sample(draftId, TemplateStatus.DRAFT, 2);
        var previous = sample(previousId, TemplateStatus.ACTIVE, 1);
        when(repository.findByIdAndTenantId(draftId, TENANT)).thenReturn(Optional.of(draft));
        when(repository.findActiveExact(TENANT, "contrat", "premium", "fr"))
                .thenReturn(Optional.of(previous));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var activated = activate.activate(draftId, TENANT, "admin");
        assertThat(activated.status()).isEqualTo(TemplateStatus.ACTIVE);
        verify(syntaxChecker).check(draft.htmlContent());

        ArgumentCaptor<DocumentTemplate> captor = ArgumentCaptor.forClass(DocumentTemplate.class);
        verify(repository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(t -> t.id().equals(previousId) && t.status() == TemplateStatus.ARCHIVED);
    }

    @Test
    void activate_invalidSyntax_keepsDraft() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT))
                .thenReturn(Optional.of(sample(id, TemplateStatus.DRAFT, 1)));
        org.mockito.Mockito.doThrow(new InvalidTemplateSyntaxException("bad"))
                .when(syntaxChecker)
                .check(any());

        assertThatThrownBy(() -> activate.activate(id, TENANT, "admin"))
                .isInstanceOf(InvalidTemplateSyntaxException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void activate_rejectsNonDraft() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT))
                .thenReturn(Optional.of(sample(id, TemplateStatus.ARCHIVED, 1)));
        assertThatThrownBy(() -> activate.activate(id, TENANT, "admin"))
                .isInstanceOf(InvalidTemplateStateException.class);
    }

    @Test
    void archive_requiresActive() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT))
                .thenReturn(Optional.of(sample(id, TemplateStatus.ACTIVE, 1)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(archive.archive(id, TENANT, "admin").status()).isEqualTo(TemplateStatus.ARCHIVED);

        when(repository.findByIdAndTenantId(id, TENANT))
                .thenReturn(Optional.of(sample(id, TemplateStatus.DRAFT, 1)));
        assertThatThrownBy(() -> archive.archive(id, TENANT, "admin"))
                .isInstanceOf(InvalidTemplateStateException.class);
    }

    @Test
    void newVersion_clonesContentWithIncrementedVersion() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT))
                .thenReturn(Optional.of(sample(id, TemplateStatus.ACTIVE, 3)));
        when(repository.nextVersion(TENANT, "contrat", "premium", "fr")).thenReturn(4);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var draft = newVersion.newVersion(id, TENANT, "admin");
        assertThat(draft.status()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(draft.version()).isEqualTo(4);
        assertThat(draft.id()).isNotEqualTo(id);
        assertThat(draft.htmlContent()).isEqualTo("<p th:text=\"${title}\">x</p>");
    }

    @Test
    void list_stripsHtmlUnlessIncludeContent() {
        UUID id = UUID.randomUUID();
        when(repository.listByTenantId(TENANT, "contrat"))
                .thenReturn(List.of(sample(id, TemplateStatus.DRAFT, 1)));

        assertThat(list.list(TENANT, "contrat", false).getFirst().htmlContent()).isEmpty();
        assertThat(list.list(TENANT, "contrat", true).getFirst().htmlContent())
                .contains("th:text");
    }

    @Test
    void resolve_throwsWhenMissing() {
        when(repository.findBestActive(any(), eq("missing"), any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolve.resolve(new TemplateResolutionRequest("missing", null, null)))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void get_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> get.get(id, TENANT)).isInstanceOf(TemplateNotFoundException.class);
    }

    private static DocumentTemplate sample(UUID id, TemplateStatus status, int version) {
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        return new DocumentTemplate(
                id,
                "contrat",
                "premium",
                "fr",
                "label",
                version,
                status,
                "<p th:text=\"${title}\">x</p>",
                TENANT,
                now,
                now,
                "admin");
    }
}
