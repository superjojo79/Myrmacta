package com.flowforge.domain.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateResolutionRequest;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.in.ActivateDocumentTemplateUseCase;
import com.flowforge.domain.document.port.in.ArchiveDocumentTemplateUseCase;
import com.flowforge.domain.document.port.in.CreateDocumentTemplateUseCase;
import com.flowforge.domain.document.port.in.GetDocumentTemplateUseCase;
import com.flowforge.domain.document.port.in.ListDocumentTemplateUseCase;
import com.flowforge.domain.document.port.in.NewDocumentTemplateVersionUseCase;
import com.flowforge.domain.document.port.in.ResolveDocumentTemplateUseCase;
import com.flowforge.domain.document.port.in.UpdateDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import com.flowforge.domain.document.port.out.TemplateSyntaxChecker;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentTemplatePortsContractTest {

    @Test
    void repository_checker_and_useCasePorts_areCallable() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        var template = new DocumentTemplate(
                id,
                "contrat",
                "premium",
                "fr",
                "label",
                1,
                TemplateStatus.DRAFT,
                "<p>ok</p>",
                "tenant_dev",
                now,
                now,
                "admin");

        DocumentTemplateRepository repo = new DocumentTemplateRepository() {
            @Override
            public DocumentTemplate save(DocumentTemplate documentTemplate) {
                return documentTemplate;
            }

            @Override
            public Optional<DocumentTemplate> findByIdAndTenantId(UUID templateId, String tenantId) {
                return Optional.of(template);
            }

            @Override
            public List<DocumentTemplate> listByTenantId(String tenantId, String templateKey) {
                return List.of(template);
            }

            @Override
            public int nextVersion(String tenantId, String templateKey, String variantKey, String locale) {
                return 2;
            }

            @Override
            public Optional<DocumentTemplate> findBestActive(
                    String tenantId, String templateKey, String variantKey, String locale) {
                return Optional.of(template);
            }

            @Override
            public Optional<DocumentTemplate> findActiveExact(
                    String tenantId, String templateKey, String variantKey, String locale) {
                return Optional.empty();
            }

            @Override
            public List<DocumentTemplate> findActiveByTemplateKeysIn(String tenantId, List<String> templateKeys) {
                return List.of(template);
            }
        };

        TemplateSyntaxChecker checker = html -> {};

        CreateDocumentTemplateUseCase create =
                command -> template;
        UpdateDocumentTemplateUseCase update = command -> template;
        ActivateDocumentTemplateUseCase activate = (templateId, tenantId, updatedBy) -> template;
        ArchiveDocumentTemplateUseCase archive = (templateId, tenantId, updatedBy) -> template;
        NewDocumentTemplateVersionUseCase newVersion = (templateId, tenantId, updatedBy) -> template;
        ListDocumentTemplateUseCase list = (tenantId, templateKey, includeContent, query) ->
                PageResult.of(List.of(template), query.page(), query.size(), 1);
        GetDocumentTemplateUseCase get = (templateId, tenantId) -> template;
        ResolveDocumentTemplateUseCase resolve = request -> template;

        assertThat(repo.findByIdAndTenantId(id, "tenant_dev")).contains(template);
        assertThat(repo.nextVersion("tenant_dev", "contrat", "premium", "fr")).isEqualTo(2);
        assertThat(repo.findBestActive("tenant_dev", "contrat", null, null)).contains(template);
        assertThat(repo.findActiveByTemplateKeysIn("tenant_dev", List.of("contrat"))).containsExactly(template);
        checker.check("<p th:text=\"${x}\">x</p>");
        assertThat(create.create(new CreateDocumentTemplateUseCase.CreateCommand(
                        "contrat", "premium", "fr", "label", "<p>ok</p>", "admin")))
                .isEqualTo(template);
        assertThat(update.update(new UpdateDocumentTemplateUseCase.UpdateCommand(
                        id, "label2", "<p>v2</p>", "admin")))
                .isEqualTo(template);
        assertThat(activate.activate(id, "tenant_dev", "admin")).isEqualTo(template);
        assertThat(archive.archive(id, "tenant_dev", "admin")).isEqualTo(template);
        assertThat(newVersion.newVersion(id, "tenant_dev", "admin")).isEqualTo(template);
        assertThat(list.list("tenant_dev", "contrat", false, PageQuery.defaults()).content())
                .containsExactly(template);
        assertThat(get.get(id, "tenant_dev")).isEqualTo(template);
        assertThat(resolve.resolve(new TemplateResolutionRequest("contrat", "premium", "fr")))
                .isEqualTo(template);
    }
}
