package be.myrmacta.domain.ebox.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * TODO §155 (D-EBOX-SEND-REGISTERED) — preuve de dépôt d'un recommandé ({@code ProofOfDispatch}, Registered Mail 1.1) : la
 * référence client (notre clé d'idempotence), l'identifiant rendu, l'empreinte de la signature et le document JSON signé tel que
 * reçu, à verser au dossier.
 *
 * @param signatureSha256 SHA-256 hexadécimal du champ {@code signature} : le journal garde l'empreinte, le dossier la preuve
 */
public record EboxProofOfDispatch(String customerReference, String providerMessageId, String signatureSha256, String json) {

    public static final int MAX_JSON = 256 * 1024;
    private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");

    public EboxProofOfDispatch {
        customerReference = EboxTexts.line(customerReference, "customerReference", 100);
        providerMessageId = EboxTexts.line(providerMessageId, "providerMessageId", 200);
        if (signatureSha256 == null || !HEX64.matcher(signatureSha256).matches()) {
            throw new IllegalArgumentException("signatureSha256 must be a lowercase SHA-256 hex digest");
        }
        Objects.requireNonNull(json, "json");
        if (json.length() > MAX_JSON) {
            throw new IllegalArgumentException("proof of dispatch is too large");
        }
    }
}
