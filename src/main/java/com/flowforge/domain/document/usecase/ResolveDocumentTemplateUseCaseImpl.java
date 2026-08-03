package com.flowforge.domain.document.usecase;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateNotFoundException;
import com.flowforge.domain.document.model.TemplateResolutionRequest;
import com.flowforge.domain.document.port.in.ResolveDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import java.util.Objects;

public final class ResolveDocumentTemplateUseCaseImpl implements ResolveDocumentTemplateUseCase {

    private final String tenantId;
    private final DocumentTemplateRepository repository;

    public ResolveDocumentTemplateUseCaseImpl(String tenantId, DocumentTemplateRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public DocumentTemplate resolve(TemplateResolutionRequest request) {
        Objects.requireNonNull(request, "request");
        String key = DocumentTemplateSupport.requireText(request.templateKey(), "templateKey");
        String variant = DocumentTemplateSupport.normalizeOptional(request.variantKey());
        String locale = DocumentTemplateSupport.normalizeOptional(request.locale());
        var normalized = new TemplateResolutionRequest(key, variant, locale);
        return repository
                .findBestActive(tenantId, normalized.templateKey(), normalized.variantKey(), normalized.locale())
                .orElseThrow(() -> new TemplateNotFoundException(normalized));
    }
}
