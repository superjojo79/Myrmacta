package be.myrmacta.infrastructure.web.connector;

/** Clé d'idempotence lue dans un corps de webhook déjà authentifié (TODO §109, D-SIGNATURE-WEBHOOK). */
interface BodyIdempotency {

    String idempotencyKey(byte[] rawBody);
}
