package be.myrmacta.domain.precheck.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import java.util.List;
import java.util.UUID;

/**
 * TODO §106 (issue #211, lot B, P9b, R7, H7, HC10) — pré-vérifications d'un dossier vues de l'agent : lire (droit de
 * lecture), relancer et décider (EDIT), exporter le journal (TENANT_ADMIN).
 */
public interface ManageDossierPrechecksUseCase {

    int LIST_LIMIT = 20;

    /** Relance avec la spécification de la dernière exécution ; mise en file ({@code PENDING}). */
    PrecheckRun request(UUID dossierId, AccessContext.User actor, PrecheckRun.Trigger trigger);

    /** Les {@value #LIST_LIMIT} dernières exécutions, la plus récente d'abord, avec leurs constats. */
    List<PrecheckRun> list(UUID dossierId, AccessContext actor);

    PrecheckRun export(UUID dossierId, UUID runId, AccessContext.User actor);

    /** @param decision {@code null} annule la décision précédente */
    PrecheckFinding decide(UUID dossierId, UUID runId, UUID findingId, PrecheckFinding.Decision decision,
            AccessContext.User actor);
}
