package be.myrmacta.domain.document.usecase;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateNotFoundException;
import be.myrmacta.domain.document.port.in.GetDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.out.DocumentTemplateRepository;
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
