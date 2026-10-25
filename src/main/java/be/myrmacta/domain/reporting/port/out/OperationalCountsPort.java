package be.myrmacta.domain.reporting.port.out;

import be.myrmacta.domain.reporting.model.OperationalCounts;

public interface OperationalCountsPort {

    OperationalCounts current(String tenantId);
}
