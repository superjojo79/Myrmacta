package be.myrmacta.infrastructure.ebox;

import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * TODO §155 (D-EBOX-SEND-AUDIT) — référence d'un destinataire sans NISS en clair : le numéro BCE d'une entreprise, public, tel
 * quel ; pour un citoyen, une empreinte HMAC-SHA256 du NISS. Un NISS n'a qu'environ un milliard de valeurs : une empreinte sans
 * clé se retrouverait par force brute. La clé est dérivée de la clé maîtresse des connecteurs
 * ({@code app.connector.crypto.master-key}) et du tenant. Sert au journal d'audit des envois et, depuis la revue du lot 2 (H2),
 * à la clé d'idempotence de la brique « Envoyer en eBox ».
 */
@Component
public class EboxRecipientHmac {

    private static final String LABEL = "myrmacta/ebox-send-audit/ssin/v1/";

    private final byte[] masterKey;

    public EboxRecipientHmac(ConnectorProperties properties) {
        String key = properties.crypto() == null ? null : properties.crypto().masterKey();
        this.masterKey = key == null || key.isBlank() ? null : Base64.getDecoder().decode(key.strip());
    }

    /** BCE en clair pour une entreprise, empreinte HMAC hexadécimale du NISS pour un citoyen. */
    public String reference(String tenantId, EboxRecipient recipient) {
        return switch (recipient) {
            case EboxRecipient.Enterprise enterprise -> enterprise.enterpriseNumber();
            case EboxRecipient.Citizen citizen -> citizen(tenantId, citizen.ssin());
        };
    }

    public String citizen(String tenantId, String ssin) {
        if (masterKey == null) {
            throw new IllegalStateException("app.connector.crypto.master-key is required to send to a citizen eBox");
        }
        try {
            Mac derive = Mac.getInstance("HmacSHA256");
            derive.init(new SecretKeySpec(masterKey, "HmacSHA256"));
            byte[] tenantKey = derive.doFinal((LABEL + tenantId).getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tenantKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(ssin.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 is unavailable", impossible);
        }
    }
}
