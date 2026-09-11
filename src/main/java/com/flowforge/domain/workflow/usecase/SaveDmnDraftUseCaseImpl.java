package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.workflow.model.DmnDraft;
import com.flowforge.domain.workflow.model.InvalidDmnException;
import com.flowforge.domain.workflow.port.in.SaveDmnDraftUseCase;
import com.flowforge.domain.workflow.port.out.DmnDraftRepository;
import com.flowforge.domain.workflow.port.out.DmnParserPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

public final class SaveDmnDraftUseCaseImpl implements SaveDmnDraftUseCase {

    private final String tenantId;
    private final DmnDraftRepository draftRepository;
    private final DmnParserPort dmnParserPort;

    public SaveDmnDraftUseCaseImpl(String tenantId, DmnDraftRepository draftRepository, DmnParserPort dmnParserPort) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository");
        this.dmnParserPort = Objects.requireNonNull(dmnParserPort, "dmnParserPort");
    }

    @Override
    public DmnDraft save(String decisionKey, String xml, String updatedBy) {
        if (xml == null || xml.isBlank()) {
            throw new InvalidDmnException("xml is required");
        }
        String extractedKey = dmnParserPort.extractDecisionKey(xml.getBytes(StandardCharsets.UTF_8));
        if (!extractedKey.equals(decisionKey)) {
            throw new InvalidDmnException("decisionKey in path ('" + decisionKey
                    + "') does not match the decision id defined in the XML ('" + extractedKey + "')");
        }
        DmnDraft draft = new DmnDraft(tenantId, decisionKey, xml, Instant.now(), updatedBy);
        return draftRepository.save(draft);
    }
}
