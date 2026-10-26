package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.precheck.port.out.PrecheckVariablesPort;
import java.util.Map;
import java.util.UUID;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Component;

/** TODO §106 (R6, R7) — variables du process actif du dossier, pour les règles d'une relance hors workflow. */
@Component
class FlowablePrecheckVariablesAdapter implements PrecheckVariablesPort {

    private final DossierWorkflowLinkRepository linkRepository;
    private final RuntimeService runtimeService;

    FlowablePrecheckVariablesAdapter(DossierWorkflowLinkRepository linkRepository, RuntimeService runtimeService) {
        this.linkRepository = linkRepository;
        this.runtimeService = runtimeService;
    }

    @Override
    public Map<String, Object> currentVariables(UUID dossierId) {
        return linkRepository.findActiveByDossierId(dossierId)
                .map(link -> link.processInstanceId())
                .filter(id -> id != null && !id.isBlank())
                .map(this::variables)
                .orElse(Map.of());
    }

    private Map<String, Object> variables(String processInstanceId) {
        try {
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            return variables == null ? Map.of() : variables;
        } catch (FlowableException gone) {
            return Map.of(); // instance terminée entre-temps : règles « non évaluées »
        }
    }
}
