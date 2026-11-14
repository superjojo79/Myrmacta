package be.myrmacta.infrastructure.signature.eid;

import java.time.Instant;

/**
 * Horodatage du document signé final (TODO §138, D-EID-TIMESTAMP). Implémentation : {@link DssDocumentTimestamper}.
 */
public interface DocumentTimestamper {

    /** Document étendu et ce que la preuve doit en dire ({@code authority} : sujet du certificat de la TSA). */
    record Result(byte[] document, Instant tokenTime, String authority, boolean authorityTrusted, String level) {}

    /**
     * @throws RfcTimestampSource.TimestampUnavailableException TSA injoignable ou réponse refusée : l'appelant
     *     décide (reprise, ou livraison sans horodatage dite dans la preuve)
     */
    Result timestamp(byte[] signedDocument);
}
