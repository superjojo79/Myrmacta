package be.myrmacta.domain.workflow.port.out;

import be.myrmacta.domain.workflow.model.WorkflowIncident;
import java.util.List;
import java.util.Optional;

/**
 * TODO §94 — jobs du moteur en échec (exécutables ou minuteurs avec exception, dead letters), toujours lus pour un seul
 * tenant.
 */
public interface WorkflowIncidentPort {

    List<WorkflowIncident> listByProcessInstance(String processInstanceId, String tenantId);

    /** Échecs du tenant, les plus récents d'abord, au plus {@code limit}. */
    List<WorkflowIncident> listByTenant(String tenantId, int limit);

    Optional<WorkflowIncident> find(String jobId, String tenantId);

    /** Remet un job en dead letter en exécution, avec {@code retries} tentatives. */
    void retry(String jobId, String tenantId, int retries);
}
