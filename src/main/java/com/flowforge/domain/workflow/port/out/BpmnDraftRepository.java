package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.BpmnDraft;
import java.util.Optional;

public interface BpmnDraftRepository {

    Optional<BpmnDraft> findByTenantIdAndProcessDefinitionKey(String tenantId, String processDefinitionKey);

    /** Upsert: overwrites any existing draft for the same {@code (tenantId, processDefinitionKey)}. */
    BpmnDraft save(BpmnDraft draft);

    /** No-op if no draft exists for this key. */
    void deleteByTenantIdAndProcessDefinitionKey(String tenantId, String processDefinitionKey);
}
