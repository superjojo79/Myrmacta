package be.myrmacta.infrastructure.persistence.ebox;

import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.infrastructure.ebox.EboxRecipientHmac;
import org.springframework.stereotype.Component;

/**
 * TODO §155 (D-EBOX-SEND-AUDIT) — référence du destinataire écrite au journal (ATREQ03) : le numéro BCE d'une entreprise, public,
 * en clair ; pour un citoyen, une empreinte HMAC-SHA256 du NISS ({@link EboxRecipientHmac}), jamais le NISS ; la version
 * ({@code SSIN_HMAC_V1}) est écrite avec chaque ligne pour qu'une rotation reste lisible.
 */
@Component
class EboxRecipientReference {

    static final String KIND_ENTERPRISE = "BCE";
    static final String KIND_CITIZEN = "SSIN_HMAC_V1";

    private final EboxRecipientHmac hmac;

    EboxRecipientReference(EboxRecipientHmac hmac) {
        this.hmac = hmac;
    }

    record Reference(String kind, String value) {}

    Reference of(String tenantId, EboxRecipient recipient) {
        return switch (recipient) {
            case EboxRecipient.Enterprise enterprise -> new Reference(KIND_ENTERPRISE, enterprise.enterpriseNumber());
            case EboxRecipient.Citizen citizen -> new Reference(KIND_CITIZEN, hmac.citizen(tenantId, citizen.ssin()));
        };
    }
}
