package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.PurgeOutboxRecord;
import be.myrmacta.domain.dossier.model.PurgeOutboxStatus;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;

public interface ListPurgeOutboxUseCase {

    record Counts(long pending, long processing, long failed, long completed) {}

    record Page(PageResult<PurgeOutboxRecord> result, Counts counts) {}

    Page list(AccessContext.User actor, PurgeOutboxStatus statusFilter, PageQuery query);
}
