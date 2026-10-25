package be.myrmacta.domain.workflow.portal.port.in;

/** Tâche complétée en interne, supprimée ou process annulé : ses liens vivants meurent (T-INVAL). */
public interface InvalidatePortalLinksForTaskUseCase {

    int onTaskEnded(String taskId);
}
