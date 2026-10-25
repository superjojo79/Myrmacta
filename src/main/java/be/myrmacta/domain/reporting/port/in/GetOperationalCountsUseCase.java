package be.myrmacta.domain.reporting.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.reporting.model.OperationalCounts;

public interface GetOperationalCountsUseCase {

    OperationalCounts get(String tenantId, AccessContext ctx);
}
