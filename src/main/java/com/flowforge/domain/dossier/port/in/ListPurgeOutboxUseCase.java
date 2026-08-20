package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.model.PurgeOutboxStatus;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;

public interface ListPurgeOutboxUseCase {

    record Counts(long pending, long processing, long failed, long completed) {}

    record Page(PageResult<PurgeOutboxRecord> result, Counts counts) {}

    Page list(AccessContext.User actor, PurgeOutboxStatus statusFilter, PageQuery query);
}
