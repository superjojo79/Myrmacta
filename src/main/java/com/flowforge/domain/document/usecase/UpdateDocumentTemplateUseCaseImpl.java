package com.flowforge.domain.document.usecase;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.InvalidTemplateStateException;
import com.flowforge.domain.document.model.TemplateNotFoundException;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.in.UpdateDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import java.time.Instant;
import java.util.Objects;

public final class UpdateDocumentTemplateUseCaseImpl implements UpdateDocumentTemplateUseCase {

    private final String tenantId;
    private final DocumentTemplateRepository repository;
    private final int maxHtmlBytes;

    public UpdateDocumentTemplateUseCaseImpl(
            String tenantId, DocumentTemplateRepository repository, int maxHtmlBytes) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.maxHtmlBytes = maxHtmlBytes;
    }

    @Override
    public DocumentTemplate update(UpdateCommand command) {
        Objects.requireNonNull(command, "command");
        DocumentTemplate current = repository
                .findByIdAndTenantId(command.id(), tenantId)
                .orElseThrow(() -> new TemplateNotFoundException(command.id()));
        if (current.status() != TemplateStatus.DRAFT) {
            throw new InvalidTemplateStateException("Only DRAFT templates can be patched");
        }
        String label = command.label() != null ? command.label() : current.label();
        String html = command.htmlContent() != null
                ? DocumentTemplateSupport.requireText(command.htmlContent(), "htmlContent")
                : current.htmlContent();
        DocumentTemplateSupport.assertHtmlSize(html, maxHtmlBytes);
        Instant now = Instant.now();
        return repository.save(new DocumentTemplate(
                current.id(),
                current.templateKey(),
                current.variantKey(),
                current.locale(),
                label,
                current.version(),
                current.status(),
                html,
                current.tenantId(),
                current.createdAt(),
                now,
                DocumentTemplateSupport.updatedByOrSystem(command.updatedBy())));
    }
}
