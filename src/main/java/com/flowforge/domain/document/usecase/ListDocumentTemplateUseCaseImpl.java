package com.flowforge.domain.document.usecase;

import com.flowforge.domain.document.DocumentTemplateSupport;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.port.in.ListDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import java.util.List;
import java.util.Objects;

public final class ListDocumentTemplateUseCaseImpl implements ListDocumentTemplateUseCase {

    private final String tenantId;
    private final DocumentTemplateRepository repository;

    public ListDocumentTemplateUseCaseImpl(String tenantId, DocumentTemplateRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public List<DocumentTemplate> list(String requestTenantId, String templateKey, boolean includeContent) {
        Objects.requireNonNull(requestTenantId, "tenantId");
        String key = DocumentTemplateSupport.normalizeOptional(templateKey);
        return repository.listByTenantId(tenantId, key).stream()
                .map(template -> includeContent ? template : stripContent(template))
                .toList();
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
