package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.ConfigPublication;
import com.flowforge.domain.workflow.model.DraftArtifactType;
import java.util.List;

/** TODO §82 (D6) — journal des publications de configuration (BPMN, DMN). */
public interface ConfigPublicationRepository {

    ConfigPublication save(ConfigPublication publication);

    /** Les plus récentes d'abord, au plus {@code limit} (1 à 200). */
    List<ConfigPublication> findRecent(String tenantId, int limit);

    /** Historique d'un artefact, plus récent d'abord. */
    List<ConfigPublication> findByArtifact(String tenantId, DraftArtifactType type, String key, int limit);
}
