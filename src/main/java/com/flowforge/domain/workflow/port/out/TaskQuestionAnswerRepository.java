package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.TaskQuestionAnswer;

public interface TaskQuestionAnswerRepository {

    void save(TaskQuestionAnswer answer);

    /**
     * TODO §68 D-PORTAL-PURGE — les réponses d'un dossier purgé (dont celles d'un tiers externe,
     * données personnelles) disparaissent avec lui. Écart préexistant trouvé en Phase 2 : la table
     * portait un {@code dossier_id} mais n'était pas dans le graphe de purge (TODO §22).
     */
    int deleteAllByDossierId(java.util.UUID dossierId);
}
