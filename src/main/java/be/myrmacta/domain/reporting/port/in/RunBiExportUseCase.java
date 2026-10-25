package be.myrmacta.domain.reporting.port.in;

import be.myrmacta.domain.reporting.UnitOfWork;
import java.time.Instant;

public interface RunBiExportUseCase {

    void run(String tenantId, Instant now, UnitOfWork unitOfWork);
}
