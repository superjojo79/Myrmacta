package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.UniqueKey;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * TODO §102 (issue #184) — lecture des valeurs uniques tenues par les dossiers non clôturés, pour le pré-contrôle des use
 * cases (erreur lisible avant écriture). La garantie elle-même est tenue à l'enregistrement du dossier (D-UNIQUE-STORE).
 */
public interface DossierUniquenessPort {

    /**
     * @param excludeDossierId dossier en cours d'écriture ({@code null} pour une création) : il ne se gêne pas lui-même
     * @return pour chaque clé déjà tenue par un autre dossier, la référence de ce dossier
     */
    Map<UniqueKey, String> findHolders(String tenantId, String dossierTypeCode, Set<UniqueKey> keys, UUID excludeDossierId);
}
