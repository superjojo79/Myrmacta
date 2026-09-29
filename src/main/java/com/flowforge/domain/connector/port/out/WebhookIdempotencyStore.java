package com.flowforge.domain.connector.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Webhook delivery idempotency (D-IDEM). Claim keys atomically with
 * {@link #beginOrGetCompleted} before side effects, then {@link #complete}.
 */
public interface WebhookIdempotencyStore {

    /**
     * Atomically claim {@code idempotencyKey} for processing, or return a prior
     * completed HTTP status within TTL.
     *
     * @return empty if this caller owns the claim and must process the delivery;
     *     {@link Optional} of the stored status if the key was already completed
     * @throws com.flowforge.domain.connector.model.WebhookIdempotencyInFlightException
     *     if another request is mid-flight on the same key
     */
    Optional<Integer> beginOrGetCompleted(UUID instanceId, String idempotencyKey);

    /** Persist the final HTTP status for a previously claimed key. */
    void complete(UUID instanceId, String idempotencyKey, int responseStatus);

    /** Abandons a claim without recording an outcome, so the same key can be retried (transient failure). */
    void release(UUID instanceId, String idempotencyKey);

    /** Read-only lookup of a completed status (never claims). */
    Optional<Integer> findResponseStatus(UUID instanceId, String idempotencyKey);
}
