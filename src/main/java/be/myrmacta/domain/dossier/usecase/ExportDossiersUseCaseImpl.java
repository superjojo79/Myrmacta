package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.DossierFieldRedactor;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.ExportDossiersQuery;
import be.myrmacta.domain.dossier.port.in.ExportDossiersUseCase;
import be.myrmacta.domain.dossier.port.out.DossierExportPort;
import be.myrmacta.domain.dossier.port.out.DossierSearchPort;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.util.Objects;
import java.util.Set;

public final class ExportDossiersUseCaseImpl implements ExportDossiersUseCase {

    private final DossierTypeRepository typeRepository;
    private final DossierExportPort exportPort;
    private final DossierAccessPolicy accessPolicy;

    public ExportDossiersUseCaseImpl(
            DossierTypeRepository typeRepository, DossierExportPort exportPort, DossierAccessPolicy accessPolicy) {
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.exportPort = Objects.requireNonNull(exportPort, "exportPort");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    @Override
    public ExportResult export(String tenantId, ExportDossiersQuery query, AccessContext ctx) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(ctx, "ctx");

        accessPolicy.assertCanViewType(tenantId, query.dossierTypeCode(), ctx);
        DossierType type = typeRepository.findByCode(tenantId, query.dossierTypeCode())
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier type not found: " + tenantId + "/" + query.dossierTypeCode()));

        DossierSearchPort.Filter filter = DossierSearchPort.Filter.restricted(
                query.status(), Set.of(query.dossierTypeCode()), query.referencePrefix());
        var dossiers = exportPort.streamForExport(tenantId, filter)
                .map(dossier -> DossierFieldRedactor.redact(dossier, type.fieldDefinitions(), ctx));
        return new ExportResult(type.fieldDefinitions(), dossiers);
    }
}
