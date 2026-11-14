package be.myrmacta.infrastructure.web.signature;

import be.myrmacta.domain.signature.model.InvalidCardSignatureInputException;
import be.myrmacta.domain.signature.port.in.CardSignatureUseCase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Forme JSON de la signature dans la page (TODO §138, D-EID-BRIDGE), partagée par le portail et « Mes signatures » :
 * binaires en base64. Aucun champ ne peut porter le PIN (D-EID-PIN) ; aucun corps n'est journalisé. Toute entrée
 * illisible (champ manquant, base64 invalide) lève {@link InvalidCardSignatureInputException} : 400 sans détail.
 */
public final class CardSignatureWire {

    private CardSignatureWire() {}

    /** {@code certificate} : DER en base64 ; {@code certificateChain} : facultative. */
    public record PrepareRequest(String certificate, List<String> certificateChain) {}

    /** Les quatre premiers champs sont repris tels quels de la réponse de {@code prepare}. */
    public record CompleteRequest(
            String certificate,
            List<String> certificateChain,
            Instant signingTime,
            String documentDigest,
            String signature) {}

    public record PrepareResponse(String digest, String hashFunction, Instant signingTime, String documentDigest) {
        public static PrepareResponse from(CardSignatureUseCase.Prepared prepared) {
            return new PrepareResponse(Base64.getEncoder().encodeToString(prepared.digest()), prepared.hashFunction(),
                    prepared.signingTime(), prepared.documentDigest());
        }
    }

    public static CardSignatureUseCase.CardInput input(PrepareRequest body) {
        if (body == null) {
            throw new InvalidCardSignatureInputException();
        }
        return new CardSignatureUseCase.CardInput(decode(body.certificate()), chain(body.certificateChain()));
    }

    public static CardSignatureUseCase.Completion completion(CompleteRequest body) {
        if (body == null) {
            throw new InvalidCardSignatureInputException();
        }
        return new CardSignatureUseCase.Completion(decode(body.certificate()), chain(body.certificateChain()),
                body.signingTime(), body.documentDigest(), decode(body.signature()));
    }

    private static List<byte[]> chain(List<String> values) {
        List<byte[]> out = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                out.add(decode(value));
            }
        }
        return out;
    }

    private static byte[] decode(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidCardSignatureInputException();
        }
        try {
            return Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidCardSignatureInputException();
        }
    }
}
