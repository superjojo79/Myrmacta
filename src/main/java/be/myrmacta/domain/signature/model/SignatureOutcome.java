package be.myrmacta.domain.signature.model;

/**
 * Issue normalisée d'une demande, indépendante du vocabulaire du fournisseur (D-SIGNATURE-OUTCOME).
 * Sa valeur est restituée au process dans la variable {@code signatureOutcome}.
 */
public enum SignatureOutcome {
    SIGNED(SignatureStatus.SIGNED),
    DECLINED(SignatureStatus.DECLINED),
    EXPIRED(SignatureStatus.EXPIRED),
    FAILED(SignatureStatus.FAILED),
    CANCELLED(SignatureStatus.CANCELLED);

    private final SignatureStatus status;

    SignatureOutcome(SignatureStatus status) {
        this.status = status;
    }

    public SignatureStatus status() {
        return status;
    }
}
