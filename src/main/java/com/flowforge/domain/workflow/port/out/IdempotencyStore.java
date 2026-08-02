package com.flowforge.domain.workflow.port.out;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyStore {

    Optional<UUID> findLinkId(String tenantId, UUID dossierId, String idempotencyKey);

    void remember(String tenantId, UUID dossierId, String idempotencyKey, UUID linkId);

    int purgeExpired();
}
