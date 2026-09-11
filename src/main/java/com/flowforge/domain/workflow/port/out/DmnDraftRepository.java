package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.DmnDraft;
import java.util.Optional;

public interface DmnDraftRepository {

    Optional<DmnDraft> findByTenantIdAndDecisionKey(String tenantId, String decisionKey);

    /** Upsert: overwrites any existing draft for the same {@code (tenantId, decisionKey)}. */
    DmnDraft save(DmnDraft draft);

    /** No-op if no draft exists for this key. */
    void deleteByTenantIdAndDecisionKey(String tenantId, String decisionKey);
}
