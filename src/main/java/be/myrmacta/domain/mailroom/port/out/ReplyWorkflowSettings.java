package be.myrmacta.domain.mailroom.port.out;

import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting;
import java.util.Optional;

/** TODO §113 (D-MAILROOM-REPLY-WORKFLOW) — réglages des workflows « réponse », un par type de dossier au plus. */
public interface ReplyWorkflowSettings {

    Optional<ReplyWorkflowSetting> find(String tenantId, String dossierTypeCode);

    /** Pose ou remplace le réglage du type. */
    void save(ReplyWorkflowSetting setting);

    void delete(String tenantId, String dossierTypeCode);
}
