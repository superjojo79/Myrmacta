package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.DossierFieldRedactor;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class GetDossierUseCaseImpl implements GetDossierUseCase {

    private final DossierRepository repository;
    private final DossierTypeRepository typeRepository;
    private final DossierAccessPolicy accessPolicy;

    public GetDossierUseCaseImpl(
            DossierRepository repository,
            DossierTypeRepository typeRepository,
            DossierAccessPolicy accessPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    @Override
    public Dossier get(UUID id, AccessContext ctx) {
        Dossier dossier = repository.findById(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + id));
        accessPolicy.assertCanView(dossier, ctx);
        return redact(dossier, ctx);
    }

    @Override
    public Dossier getByReference(String tenantId, String reference, AccessContext ctx) {
        Dossier dossier = repository.findByReference(
                        Objects.requireNonNull(tenantId, "tenantId"),
                        Objects.requireNonNull(reference, "reference"))
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier not found: " + tenantId + "/" + reference));
        accessPolicy.assertCanView(dossier, ctx);
        return redact(dossier, ctx);
    }

    private Dossier redact(Dossier dossier, AccessContext ctx) {
        List<FieldDefinition> fieldDefinitions = typeRepository
                .findByCode(dossier.tenantId(), dossier.dossierTypeCode())
                .map(DossierType::fieldDefinitions)
                .orElseGet(List::of);
        return DossierFieldRedactor.redact(dossier, fieldDefinitions, ctx);
    }
}
