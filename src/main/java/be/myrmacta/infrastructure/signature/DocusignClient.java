package be.myrmacta.infrastructure.signature;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Opérations DocuSign appelées par l'adaptateur (TODO §109, D-SIGNATURE-HTTP). L'hôte OAuth est
 * celui du bac à sable ou de la production. L'hôte du compte vient de {@code userinfo} et reste
 * sur la liste du même environnement.
 */
interface DocusignClient {

    UUID createEnvelope(
            String name, boolean ordered, String externalId, Instant deadline, List<SignerSpec> signers, byte[] pdf);

    void voidEnvelope(UUID envelopeId);

    /**
     * Vue à usage unique, demandée à l'ouverture. Vide si ce n'est pas encore le tour du signataire.
     * L'hôte du lien est contrôlé avant le retour. {@code returnUrl} ne porte pas le jeton Myrmacta.
     */
    Optional<String> recipientView(UUID envelopeId, int signerPosition, String returnUrl);

    byte[] downloadSignedPdf(UUID envelopeId);

    byte[] downloadCertificate(UUID envelopeId);

    record SignerSpec(String name, String email, int position) {}
}
