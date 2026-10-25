package be.myrmacta.domain.reporting.usecase;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.reporting.SlaReportAccess;
import be.myrmacta.domain.reporting.model.OperationalCounts;
import be.myrmacta.domain.reporting.port.in.GetOperationalCountsUseCase;
import be.myrmacta.domain.reporting.port.out.OperationalCountsPort;
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
        // Role gate only: allowedTypeCodes() throws for a caller without at least one of
        // TENANT_ADMIN/WORKFLOW_ADMIN/DOSSIER_MANAGER (mirrors the controller's @PreAuthorize
        // as a defense-in-depth check for any non-HTTP caller). The returned set is a
        // per-dossier-type ACL scope and is intentionally unused here: these counts are
        // tenant-wide by design (see FlowableOperationalCountsAdapter) and are never filtered
        // by dossier type, so a DOSSIER_MANAGER sees them regardless of which types they hold
        // a VIEW grant on.
        SlaReportAccess.allowedTypeCodes(aclRepository, tenantId, ctx);
        return countsPort.current(tenantId);
    }
}
