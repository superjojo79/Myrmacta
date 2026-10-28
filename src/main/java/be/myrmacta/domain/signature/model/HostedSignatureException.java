package be.myrmacta.domain.signature.model;

/**
 * Le signataire a demandé de signer dans Myrmacta alors que le fournisseur héberge la cérémonie
 * (TODO §109, D-SIGNATURE-LINK).
 */
public final class HostedSignatureException extends RuntimeException {

    public HostedSignatureException() {
        super("signature is hosted by the provider");
    }
}
