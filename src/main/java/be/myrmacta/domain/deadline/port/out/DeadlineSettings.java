package be.myrmacta.domain.deadline.port.out;

import be.myrmacta.domain.deadline.model.DeadlineSetting;
import java.util.List;
import java.util.Optional;

/** TODO §115 (D-DEADLINE-FIELD) — réglages d'échéance, un par type au plus. */
public interface DeadlineSettings {

    Optional<DeadlineSetting> find(String tenantId, String dossierTypeCode);

    List<DeadlineSetting> findAll(String tenantId);

    void save(DeadlineSetting setting);

    void delete(String tenantId, String dossierTypeCode);
}
