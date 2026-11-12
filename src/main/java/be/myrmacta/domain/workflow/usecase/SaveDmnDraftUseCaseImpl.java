package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.workflow.DmnXmlVersions;
import be.myrmacta.domain.workflow.model.DmnDraft;
import be.myrmacta.domain.workflow.model.InvalidDmnException;
import be.myrmacta.domain.workflow.port.in.SaveDmnDraftUseCase;
import be.myrmacta.domain.workflow.port.out.DmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
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
        // TODO §130 (D-DMN-OPEN-CONVERT) : dmn-js n'ouvre que le 1.3 — normaliser avant validation et stock.
        String normalized = DmnXmlVersions.to13(xml);
        String extractedKey = dmnParserPort.extractDecisionKey(normalized.getBytes(StandardCharsets.UTF_8));
        if (!extractedKey.equals(decisionKey)) {
            throw new InvalidDmnException("decisionKey in path ('" + decisionKey
                    + "') does not match the decision id defined in the XML ('" + extractedKey + "')");
        }
        // TODO §82 (D6) : reprise humaine d'un brouillon automatisé → provenance UI, note conservée.
        DmnDraft previous = draftRepository.findByTenantIdAndDecisionKey(tenantId, decisionKey).orElse(null);
        DmnDraft draft = new DmnDraft(tenantId, decisionKey, normalized, Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                updatedBy, be.myrmacta.domain.workflow.model.Provenance.UI,
                previous == null ? null : previous.note(), previous == null ? null : previous.pilotUserId());
        return draftRepository.save(draft);
    }
}
