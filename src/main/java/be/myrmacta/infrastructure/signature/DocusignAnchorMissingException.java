package be.myrmacta.infrastructure.signature;

/** Le PDF ne contient pas l'ancre attendue (D-SIGNATURE-ANCHOR). L'enveloppe n'est pas créée. */
final class DocusignAnchorMissingException extends DocusignCallException {

    DocusignAnchorMissingException() {
        super("the document is missing a signature anchor");
    }
}
