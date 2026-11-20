package be.myrmacta.infrastructure.persistence.ebox;

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
 * TODO §155 (D-EBOX-SEND-AUDIT) — référence du destinataire écrite au journal (ATREQ03) : le numéro BCE d'une entreprise, public,
 * en clair ; pour un citoyen, une empreinte HMAC-SHA256 du NISS, jamais le NISS. Un NISS n'a qu'environ un milliard de valeurs :
 * une empreinte sans clé se retrouverait par force brute. La clé est dérivée de la clé maîtresse des connecteurs
 * ({@code app.connector.crypto.master-key}, déjà requise pour chiffrer les identifiants eBox) et du tenant ; la version
 * ({@code SSIN_HMAC_V1}) est écrite avec chaque ligne pour qu'une rotation reste lisible.
 */
@Component
class EboxRecipientReference {

    static final String KIND_ENTERPRISE = "BCE";
    static final String KIND_CITIZEN = "SSIN_HMAC_V1";
    private static final String LABEL = "myrmacta/ebox-send-audit/ssin/v1/";

    private final byte[] masterKey;

    EboxRecipientReference(ConnectorProperties properties) {
        String key = properties.crypto() == null ? null : properties.crypto().masterKey();
        this.masterKey = key == null || key.isBlank() ? null : Base64.getDecoder().decode(key.strip());
    }

    record Reference(String kind, String value) {}

    Reference of(String tenantId, EboxRecipient recipient) {
        return switch (recipient) {
            case EboxRecipient.Enterprise enterprise -> new Reference(KIND_ENTERPRISE, enterprise.enterpriseNumber());
            case EboxRecipient.Citizen citizen -> new Reference(KIND_CITIZEN, hmac(tenantId, citizen.ssin()));
        };
    }

    private String hmac(String tenantId, String ssin) {
        if (masterKey == null) {
            throw new IllegalStateException("app.connector.crypto.master-key is required to audit a citizen eBox send");
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
