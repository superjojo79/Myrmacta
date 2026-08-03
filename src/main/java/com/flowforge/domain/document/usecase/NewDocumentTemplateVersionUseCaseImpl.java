package com.flowforge.domain.document.usecase;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateNotFoundException;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.in.NewDocumentTemplateVersionUseCase;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class NewDocumentTemplateVersionUseCaseImpl implements NewDocumentTemplateVersionUseCase {

    private final String tenantId;
    private final DocumentTemplateRepository repository;

    public NewDocumentTemplateVersionUseCaseImpl(String tenantId, DocumentTemplateRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public DocumentTemplate newVersion(UUID id, String requestTenantId, String updatedBy) {
        Objects.requireNonNull(requestTenantId, "tenantId");
        DocumentTemplate current = repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new TemplateNotFoundException(id));
        int version = repository.nextVersion(
                tenantId, current.templateKey(), current.variantKey(), current.locale());
        Instant now = Instant.now();
        String by = DocumentTemplateSupport.updatedByOrSystem(updatedBy);
        return repository.save(new DocumentTemplate(
                UUID.randomUUID(),
                current.templateKey(),
                current.variantKey(),
                current.locale(),
                current.label(),
                version,
                TemplateStatus.DRAFT,
                current.htmlContent(),
                tenantId,
                now,
                now,
                by));
    }
}
