package be.myrmacta.domain.document.usecase;

import be.myrmacta.domain.document.DocumentTemplateSupport;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.port.in.ListDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.out.DocumentTemplateRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.util.Objects;

public final class ListDocumentTemplateUseCaseImpl implements ListDocumentTemplateUseCase {

    private final String tenantId;
    private final DocumentTemplateRepository repository;

    public ListDocumentTemplateUseCaseImpl(String tenantId, DocumentTemplateRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public PageResult<DocumentTemplate> list(
            String requestTenantId, String templateKey, boolean includeContent, PageQuery query) {
        Objects.requireNonNull(requestTenantId, "tenantId");
        Objects.requireNonNull(query, "query");
        String key = DocumentTemplateSupport.normalizeOptional(templateKey);
        PageResult<DocumentTemplate> page = repository.pageByTenantId(tenantId, key, query);
        var content = page.content().stream()
                .map(template -> includeContent ? template : stripContent(template))
                .toList();
        return PageResult.of(content, page.page(), page.size(), page.totalElements());
    }

    private static DocumentTemplate stripContent(DocumentTemplate template) {
        return new DocumentTemplate(
                template.id(),
                template.templateKey(),
                template.variantKey(),
                template.locale(),
                template.label(),
                template.version(),
                template.status(),
                "",
                template.tenantId(),
                template.createdAt(),
                template.updatedAt(),
                template.updatedBy());
    }
}
