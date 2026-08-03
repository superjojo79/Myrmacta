package com.flowforge.domain.document.usecase;

import com.flowforge.domain.document.DocumentTemplateSupport;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.InvalidTemplateStateException;
import com.flowforge.domain.document.model.TemplateNotFoundException;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.in.ActivateDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import com.flowforge.domain.document.port.out.TemplateSyntaxChecker;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ActivateDocumentTemplateUseCaseImpl implements ActivateDocumentTemplateUseCase {

    private final String tenantId;
    private final DocumentTemplateRepository repository;
    private final TemplateSyntaxChecker syntaxChecker;

    public ActivateDocumentTemplateUseCaseImpl(
            String tenantId, DocumentTemplateRepository repository, TemplateSyntaxChecker syntaxChecker) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.syntaxChecker = Objects.requireNonNull(syntaxChecker, "syntaxChecker");
    }

    @Override
    public DocumentTemplate activate(UUID id, String requestTenantId, String updatedBy) {
        Objects.requireNonNull(requestTenantId, "tenantId");
        DocumentTemplate current = repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new TemplateNotFoundException(id));
        if (current.status() != TemplateStatus.DRAFT) {
            throw new InvalidTemplateStateException("Only DRAFT templates can be activated");
        }
        syntaxChecker.check(current.htmlContent());
        Instant now = Instant.now();
        String by = DocumentTemplateSupport.updatedByOrSystem(updatedBy);
        repository
                .findActiveExact(tenantId, current.templateKey(), current.variantKey(), current.locale())
                .ifPresent(active -> repository.save(new DocumentTemplate(
                        active.id(),
                        active.templateKey(),
                        active.variantKey(),
                        active.locale(),
                        active.label(),
                        active.version(),
                        TemplateStatus.ARCHIVED,
                        active.htmlContent(),
                        active.tenantId(),
                        active.createdAt(),
                        now,
                        by)));
        return repository.save(new DocumentTemplate(
                current.id(),
                current.templateKey(),
                current.variantKey(),
                current.locale(),
                current.label(),
                current.version(),
                TemplateStatus.ACTIVE,
                current.htmlContent(),
                current.tenantId(),
                current.createdAt(),
                now,
                by));
    }
}
