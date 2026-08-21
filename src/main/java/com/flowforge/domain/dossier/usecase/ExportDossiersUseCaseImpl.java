package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.DossierFieldRedactor;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.ExportDossiersQuery;
import com.flowforge.domain.dossier.port.in.ExportDossiersUseCase;
import com.flowforge.domain.dossier.port.out.DossierExportPort;
import com.flowforge.domain.dossier.port.out.DossierSearchPort;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
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
