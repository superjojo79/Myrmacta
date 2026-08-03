package com.flowforge.domain.document.usecase;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateNotFoundException;
import com.flowforge.domain.document.port.in.GetDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import java.util.Objects;
import java.util.UUID;

public final class GetDocumentTemplateUseCaseImpl implements GetDocumentTemplateUseCase {

    private final String tenantId;
    private final DocumentTemplateRepository repository;

    public GetDocumentTemplateUseCaseImpl(String tenantId, DocumentTemplateRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public DocumentTemplate get(UUID id, String requestTenantId) {
        Objects.requireNonNull(requestTenantId, "tenantId");
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new TemplateNotFoundException(id));
    }
}
