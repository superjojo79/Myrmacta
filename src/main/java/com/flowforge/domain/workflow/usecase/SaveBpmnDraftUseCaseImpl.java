package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.workflow.model.BpmnDraft;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.port.in.SaveBpmnDraftUseCase;
import com.flowforge.domain.workflow.port.out.BpmnDraftRepository;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

public final class SaveBpmnDraftUseCaseImpl implements SaveBpmnDraftUseCase {

    private final String tenantId;
    private final BpmnDraftRepository draftRepository;
    private final BpmnParserPort bpmnParserPort;

    public SaveBpmnDraftUseCaseImpl(
            String tenantId, BpmnDraftRepository draftRepository, BpmnParserPort bpmnParserPort) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository");
        this.bpmnParserPort = Objects.requireNonNull(bpmnParserPort, "bpmnParserPort");
    }

    @Override
    public BpmnDraft save(String processDefinitionKey, String xml, String updatedBy) {
        if (xml == null || xml.isBlank()) {
            throw new InvalidBpmnException("xml is required");
        }
        String extractedKey = bpmnParserPort.extractProcessDefinitionKey(xml.getBytes(StandardCharsets.UTF_8));
        if (!extractedKey.equals(processDefinitionKey)) {
            throw new InvalidBpmnException("processDefinitionKey in path ('" + processDefinitionKey
                    + "') does not match the process id defined in the XML ('" + extractedKey + "')");
        }
        BpmnDraft draft = new BpmnDraft(tenantId, processDefinitionKey, xml, Instant.now(), updatedBy);
        return draftRepository.save(draft);
    }
}
