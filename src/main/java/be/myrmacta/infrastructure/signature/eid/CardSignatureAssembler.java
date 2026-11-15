package be.myrmacta.infrastructure.signature.eid;

import be.myrmacta.domain.signature.port.out.SignaturePort.CardSignatureDigest;
import be.myrmacta.domain.signature.port.out.SignaturePort.CardSignatureInput;

/**
 * Signature PAdES en deux temps, la clé restant sur la carte (TODO §138, D-EID-BRIDGE) : le serveur
 * calcule le condensé, le pont navigateur le fait signer par la carte, le serveur assemble le PDF.
 * Sans état : les deux appels reçoivent les mêmes entrées et doivent retomber sur le même condensé.
 */
public interface CardSignatureAssembler {

    /** @throws IllegalArgumentException certificat illisible ou type de clé non pris en charge */
    CardSignatureDigest prepare(CardSignatureInput input);

    /**
     * @param signatureValue valeur rendue par le pont : RSA PKCS#1 v1.5, ou ECDSA brut (r||s) ou DER
     * @throws IllegalArgumentException certificat ou valeur de signature illisible
     */
    byte[] assemble(CardSignatureInput input, byte[] signatureValue);
}
