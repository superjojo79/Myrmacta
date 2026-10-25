package be.myrmacta.domain.document.usecase;

import be.myrmacta.domain.document.DocumentTemplateSupport;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.InvalidTemplateStateException;
import be.myrmacta.domain.document.model.TemplateNotFoundException;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.document.port.in.ArchiveDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.out.DocumentTemplateRepository;
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
