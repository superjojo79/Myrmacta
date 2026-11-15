package be.myrmacta.infrastructure.signature.eid;

import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import java.util.Set;

/**
 * Vérifie un PDF signé hors de Myrmacta avec une carte eID (TODO §138, D-EID-VALIDATION). Sans état
 * et sans écriture. La vérification peut joindre le réseau pour la révocation : l'appelant
 * l'exécute hors transaction.
 */
public interface SignedPdfVerifier {

    /**
     * @param baseDocument le PDF remis au signataire : la source, suivie des signatures déjà acceptées
     * @param signedDocument le PDF téléversé
     * @param usedCertificateFingerprints SHA-256 (hexadécimal minuscule) des certificats qui ont déjà
     *     signé pour un autre signataire de la demande
     * @return le verdict ; un fichier refusé ne lève pas d'exception
     */
    SignedUploadVerdict verify(byte[] baseDocument, byte[] signedDocument, Set<String> usedCertificateFingerprints);
}
