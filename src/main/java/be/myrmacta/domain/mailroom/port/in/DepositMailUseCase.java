package be.myrmacta.domain.mailroom.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.mailroom.model.DepositFile;
import be.myrmacta.domain.mailroom.model.DepositPoint;
import be.myrmacta.domain.mailroom.model.DepositResult;
import java.util.List;
import java.util.UUID;

/** TODO §113 (D-MAILROOM-DEPOSIT) — dépôt manuel de PDF : chaque fichier devient un courrier qui suit le tri du point de dépôt. */
public interface DepositMailUseCase {

    int MAX_FILES = 20;

    /** Points de dépôt actifs dont l'utilisateur peut créer le type de dossier (ACL EDIT). */
    List<DepositPoint> points(AccessContext.User actor);

    /** Le point de dépôt, s'il est actif et si l'utilisateur peut créer son type ; sinon 404 ou 403. */
    DepositPoint point(UUID pointId, AccessContext.User actor);

    /**
     * Un fichier : contrôlé (nom, taille, signature PDF), puis remis comme un courrier du point de dépôt. L'appelant porte la
     * transaction, une par fichier.
     *
     * @throws be.myrmacta.domain.mailroom.model.DepositRefusedException fichier refusé avant toute écriture
     */
    DepositResult deposit(UUID pointId, DepositFile file, String sender, String subject, AccessContext.User actor);
}
