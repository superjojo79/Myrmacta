package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.workflow.model.BpmnDraft;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.domain.workflow.port.in.SaveBpmnDraftUseCase;
import be.myrmacta.domain.workflow.port.out.BpmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
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
        // TODO §82 (D6) : un humain qui reprend un brouillon MCP/assistant en devient l'auteur (provenance UI) mais ne perd pas la
        // configuration de workflow proposée avec lui — elle serait sinon silencieusement abandonnée à la publication.
        BpmnDraft previous = draftRepository.findByTenantIdAndProcessDefinitionKey(tenantId, processDefinitionKey).orElse(null);
        BpmnDraft draft = new BpmnDraft(tenantId, processDefinitionKey, xml, Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                updatedBy, be.myrmacta.domain.workflow.model.Provenance.UI,
                previous == null ? null : previous.proposedConfig(), previous == null ? null : previous.pilotUserId(),
                previous == null ? null : previous.configProvenance(), previous == null ? null : previous.configAuthor());
        return draftRepository.save(draft);
    }
}
