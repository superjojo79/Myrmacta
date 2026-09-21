package com.flowforge.domain.reporting.port.out;

import com.flowforge.domain.reporting.model.SlaAlertCandidate;
import java.util.List;

public interface SlaAlertCandidatePort {

    /** Every dossier of the tenant, scored or not. The use case ignores a null target. */
    List<SlaAlertCandidate> listCandidates(String tenantId);
}
