package com.flowforge.domain.connector.port.out;

import java.util.Optional;
import java.util.UUID;

public interface WebhookIdempotencyStore {

    Optional<Integer> findResponseStatus(UUID instanceId, String idempotencyKey);

    void store(UUID instanceId, String idempotencyKey, int responseStatus);
}
