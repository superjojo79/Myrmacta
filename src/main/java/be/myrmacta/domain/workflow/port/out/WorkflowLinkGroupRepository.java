package be.myrmacta.domain.workflow.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * TODO §114 (D-GROUP-LINK) — groupe destinataire d'un process, porté par son lien de workflow : ses tâches créées sans assigné
 * ni candidat reçoivent ce groupe comme candidat. Posé par la plateforme seule (routage, réaffectation).
 */
public interface WorkflowLinkGroupRepository {

    /** Pose ou remplace le groupe du lien. */
    void assign(String tenantId, UUID linkId, String groupCode);

    Optional<String> findGroup(UUID linkId);
}
