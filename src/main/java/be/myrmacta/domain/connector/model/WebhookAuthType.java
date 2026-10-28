package be.myrmacta.domain.connector.model;

public enum WebhookAuthType {
    HMAC_SHA256,
    SHARED_SECRET_TOKEN,
    /** Corps brut signé par Yousign (`x-yousign-signature-256`), clé d'idempotence = `event_id`. TODO §109. */
    YOUSIGN_HMAC
}
