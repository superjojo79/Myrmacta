package be.myrmacta.domain.document.usecase;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.port.in.ListDocumentTemplatesForDossierTypeUseCase;
import be.myrmacta.domain.document.port.out.DocumentTemplateRepository;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.in.GetDossierTypeUseCase;
import java.util.List;
import java.util.Objects;

public final class ListDocumentTemplatesForDossierTypeUseCaseImpl implements ListDocumentTemplatesForDossierTypeUseCase {

    private final String tenantId;
    private final GetDossierTypeUseCase getDossierTypeUseCase;
    private final DocumentTemplateRepository repository;

    public ListDocumentTemplatesForDossierTypeUseCaseImpl(
            String tenantId, GetDossierTypeUseCase getDossierTypeUseCase, DocumentTemplateRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.getDossierTypeUseCase = Objects.requireNonNull(getDossierTypeUseCase, "getDossierTypeUseCase");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public List<DocumentTemplate> list(String requestTenantId, String dossierTypeCode, AccessContext ctx) {
        Objects.requireNonNull(requestTenantId, "tenantId");
        Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
        Objects.requireNonNull(ctx, "ctx");
        // D-FORM-DOCGEN-TEMPLATE-SCOPE: reusing GetDossierTypeUseCase enforces the same VIEW
        // permission check as reading the dossier type itself (DossierAccessPolicy) -- a task
        // holder who can see a dossier of this type can see which templates it allows.
        DossierType type = getDossierTypeUseCase.getByCode(tenantId, dossierTypeCode, ctx);
        if (type.documentTemplateKeys().isEmpty()) {
            return List.of();
        }
        return repository.findActiveByTemplateKeysIn(tenantId, type.documentTemplateKeys());
    }
}
