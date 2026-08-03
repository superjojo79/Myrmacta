package com.flowforge.domain.document.usecase;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.InvalidTemplateStateException;
import com.flowforge.domain.document.model.TemplateNotFoundException;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.in.ArchiveDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ArchiveDocumentTemplateUseCaseImpl implements ArchiveDocumentTemplateUseCase {

    private final String tenantId;
    private final DocumentTemplateRepository repository;

    public ArchiveDocumentTemplateUseCaseImpl(String tenantId, DocumentTemplateRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public DocumentTemplate archive(UUID id, String requestTenantId, String updatedBy) {
        Objects.requireNonNull(requestTenantId, "tenantId");
        DocumentTemplate current = repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new TemplateNotFoundException(id));
        if (current.status() != TemplateStatus.ACTIVE) {
            throw new InvalidTemplateStateException("Only ACTIVE templates can be archived");
        }
        Instant now = Instant.now();
        return repository.save(new DocumentTemplate(
                current.id(),
                current.templateKey(),
                current.variantKey(),
                current.locale(),
                current.label(),
                current.version(),
                TemplateStatus.ARCHIVED,
                current.htmlContent(),
                current.tenantId(),
                current.createdAt(),
                now,
                DocumentTemplateSupport.updatedByOrSystem(updatedBy)));
    }
}
