package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.PurgeOutboxStatus;
import be.myrmacta.domain.dossier.port.in.ListPurgeOutboxUseCase;
import be.myrmacta.domain.dossier.port.out.PurgeOutboxRepository;
import be.myrmacta.domain.shared.PageQuery;
import java.util.Objects;

public final class ListPurgeOutboxUseCaseImpl implements ListPurgeOutboxUseCase {

    private final PurgeOutboxRepository purgeOutboxRepository;
    private final TenantAdminGuard tenantAdminGuard;

    public ListPurgeOutboxUseCaseImpl(
            PurgeOutboxRepository purgeOutboxRepository, TenantAdminGuard tenantAdminGuard) {
        this.purgeOutboxRepository = Objects.requireNonNull(purgeOutboxRepository, "purgeOutboxRepository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
    }

    @Override
    public Page list(AccessContext.User actor, PurgeOutboxStatus statusFilter, PageQuery query) {
        tenantAdminGuard.requireTenantAdmin(actor);
        Objects.requireNonNull(query, "query");
        Counts counts = new Counts(
                purgeOutboxRepository.countByStatus(PurgeOutboxStatus.PENDING),
                purgeOutboxRepository.countByStatus(PurgeOutboxStatus.PROCESSING),
                purgeOutboxRepository.countByStatus(PurgeOutboxStatus.FAILED),
                purgeOutboxRepository.countByStatus(PurgeOutboxStatus.COMPLETED));
        return new Page(purgeOutboxRepository.page(statusFilter, query), counts);
    }
}
