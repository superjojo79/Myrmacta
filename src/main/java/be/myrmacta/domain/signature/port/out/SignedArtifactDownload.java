package be.myrmacta.domain.signature.port.out;

import be.myrmacta.domain.signature.model.SignatureRequest;

/**
 * Télécharge le PDF signé et la preuve d'une demande hébergée, hors du fil du webhook
 * (TODO §109, D-SIGNATURE-WEBHOOK). L'infrastructure exécute l'appel dans sa propre transaction.
 */
@FunctionalInterface
public interface SignedArtifactDownload {

    void ensure(SignatureRequest request);
}
