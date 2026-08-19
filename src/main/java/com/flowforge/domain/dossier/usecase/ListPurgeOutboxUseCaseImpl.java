package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.PurgeOutboxStatus;
import com.flowforge.domain.dossier.port.in.ListPurgeOutboxUseCase;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import java.util.Objects;

public final class ListPurgeOutboxUseCaseImpl implements ListPurgeOutboxUseCase {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final PurgeOutboxRepository purgeOutboxRepository;
    private final TenantAdminGuard tenantAdminGuard;

    public ListPurgeOutboxUseCaseImpl(
            PurgeOutboxRepository purgeOutboxRepository, TenantAdminGuard tenantAdminGuard) {
        this.purgeOutboxRepository = Objects.requireNonNull(purgeOutboxRepository, "purgeOutboxRepository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
    }

    @Override
    public Page list(AccessContext.User actor, PurgeOutboxStatus statusFilter, int limit) {
        tenantAdminGuard.requireTenantAdmin(actor);
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        Counts counts = new Counts(
                purgeOutboxRepository.countByStatus(PurgeOutboxStatus.PENDING),
                purgeOutboxRepository.countByStatus(PurgeOutboxStatus.PROCESSING),
                purgeOutboxRepository.countByStatus(PurgeOutboxStatus.FAILED),
                purgeOutboxRepository.countByStatus(PurgeOutboxStatus.COMPLETED));
        return new Page(purgeOutboxRepository.list(statusFilter, effectiveLimit), counts);
    }
}
