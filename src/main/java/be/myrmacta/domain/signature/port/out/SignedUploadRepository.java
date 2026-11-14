package be.myrmacta.domain.signature.port.out;

import be.myrmacta.domain.signature.model.SignedUpload;
import java.util.List;
import java.util.UUID;

/**
 * Persistance des téléversements acceptés (TODO §138). Deux contraintes d'unicité par demande : le rang
 * et l'empreinte du certificat. Sous PostgreSQL, une violation condamne la transaction même attrapée :
 * l'appelant verrouille la demande ({@link SignatureRequestRepository#findByIdForUpdate}) et relit les
 * téléversements avant d'enregistrer.
 */
public interface SignedUploadRepository {

    /** Téléversements acceptés d'une demande, par rang croissant. */
    List<SignedUpload> findByRequestId(UUID requestId);

    void save(SignedUpload upload);

    /**
     * Références de stockage des documents de travail d'un dossier : ce ne sont pas des éléments du
     * dossier, la purge les supprime elle-même.
     */
    List<String> findStorageRefsByDossierId(UUID dossierId);

    /** Purge du dossier : supprime les téléversements de ses demandes. */
    int deleteAllByDossierId(UUID dossierId);
}
