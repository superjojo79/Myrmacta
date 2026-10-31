package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.MyWorkflowTask;

public interface ListMyTasksUseCase {

    PageResult<MyWorkflowTask> listMine(String assigneeId, PageQuery query);

    /**
     * TODO §114 (D-GROUP-ASSIGN) — tâches de l'utilisateur, plus les tâches libres de ses groupes sur les dossiers qu'il peut
     * modifier (il peut alors les prendre, §92).
     */
    default PageResult<MyWorkflowTask> listMine(AccessContext.User actor, PageQuery query) {
        return listMine(actor.userId(), query);
    }
}
