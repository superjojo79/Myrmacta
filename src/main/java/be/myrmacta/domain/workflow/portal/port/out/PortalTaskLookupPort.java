package be.myrmacta.domain.workflow.portal.port.out;

import be.myrmacta.domain.workflow.portal.model.PortalTaskDefinition;
import java.util.Optional;

/** Lecture d'une tâche Flowable encore ouverte (nom, consignes, définition portail). */
public interface PortalTaskLookupPort {

    /** Vide si la tâche n'existe plus (complétée, supprimée, process annulé). */
    Optional<OpenTask> findOpenTask(String taskId);

    /** Définition portail de la tâche ouverte, vide si elle n'existe plus ou n'est pas externe. */
    Optional<PortalTaskDefinition> findExternalTaskDefinition(String taskId);

    record OpenTask(String taskId, String processInstanceId, String name, String instructions) {}
}
