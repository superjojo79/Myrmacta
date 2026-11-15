package be.myrmacta.domain.signature.model;

/**
 * Motif de refus d'un PDF signé téléversé par un signataire (TODO §138, D-EID-UPLOAD). Le motif ne
 * décrit que le fichier du signataire : il peut lui être restitué tel quel, sans rien révéler de la
 * demande ni des autres signataires.
 */
public enum SignedUploadRejection {
    /** Le fichier n'est pas un PDF lisible, ou il est chiffré. */
    NOT_A_PDF,
    /** Le fichier ne prolonge pas, octet pour octet, le document remis au signataire (D-EID-BINDING). */
    DOCUMENT_MISMATCH,
    /** Un autre signataire a signé entre le téléchargement et le téléversement : le document a changé. */
    STALE_DOCUMENT,
    /** Aucune signature n'a été ajoutée au document remis. */
    NO_NEW_SIGNATURE,
    /** Plus d'une signature a été ajoutée en un téléversement. */
    SEVERAL_SIGNATURES,
    /** Le contenu a été modifié au-delà de l'ajout d'une signature, ou la signature ne couvre pas tout le fichier. */
    CONTENT_MODIFIED,
    /** La signature est cryptographiquement invalide. */
    SIGNATURE_INVALID,
    /** Le certificat ne remonte pas à une autorité de confiance configurée, ou il est hors validité. */
    CERTIFICATE_UNTRUSTED,
    /** Le certificat n'est pas un certificat de signature qualifié sur dispositif qualifié (D-EID-QUALIFIED). */
    CERTIFICATE_NOT_QUALIFIED,
    /** Le certificat est révoqué ou suspendu. */
    CERTIFICATE_REVOKED,
    /** L'état de révocation n'a pas pu être obtenu : à réessayer plus tard (D-EID-REVOCATION). */
    REVOCATION_UNAVAILABLE,
    /** Ce certificat a déjà signé pour un autre signataire de la même demande. */
    DUPLICATE_SIGNER
}
