package com.flowforge.domain.reporting.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.reporting.SlaReportAccess;
import com.flowforge.domain.reporting.model.OperationalCounts;
import com.flowforge.domain.reporting.port.in.GetOperationalCountsUseCase;
import com.flowforge.domain.reporting.port.out.OperationalCountsPort;
import java.util.Objects;

public final class GetOperationalCountsUseCaseImpl implements GetOperationalCountsUseCase {

    private final OperationalCountsPort countsPort;
    private final DossierTypeAclRepository aclRepository;

    public GetOperationalCountsUseCaseImpl(
            OperationalCountsPort countsPort, DossierTypeAclRepository aclRepository) {
        this.countsPort = Objects.requireNonNull(countsPort, "countsPort");
        this.aclRepository = Objects.requireNonNull(aclRepository, "aclRepository");
    }

    @Override
    public OperationalCounts get(String tenantId, AccessContext ctx) {
        SlaReportAccess.allowedTypeCodes(aclRepository, tenantId, ctx);
        return countsPort.current(tenantId);
    }
}
