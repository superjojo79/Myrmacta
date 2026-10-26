package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import be.myrmacta.domain.precheck.port.in.RunDossierPrecheckUseCase;
import be.myrmacta.domain.precheck.port.out.PrecheckAvailabilityPort;
import be.myrmacta.domain.workflow.palette.BlockAttributeRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * TODO §106 (issue #211, lot B, P3, HC5, HC6, D-PRECHECK-BLOCK) — brique « Pré-vérification IA » : toujours asynchrone
 * (exigé au déploiement), attributs littéraux seulement. Le résultat est rangé même en échec ; un échec (fournisseur,
 * budget, réponse invalide) lève ensuite une exception, donc un incident rejouable (§94) dont le message est générique.
 * La variable de résultat ne porte que des comptes déterministes (C5).
 */
@Component("aiPrecheckTask")
class AiPrecheckDelegate implements JavaDelegate {

    static final String DELEGATE = "aiPrecheckTask";
    private static final List<String> ATTRIBUTES = List.of(PrecheckSpec.PIECES, PrecheckSpec.FIELDS,
            PrecheckSpec.QUESTIONS, PrecheckSpec.RULES, PrecheckSpec.RESULT);

    private final RunDossierPrecheckUseCase useCase;
    private final PrecheckAvailabilityPort availability;

    AiPrecheckDelegate(RunDossierPrecheckUseCase useCase, PrecheckAvailabilityPort availability) {
        this.useCase = useCase;
        this.availability = availability;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String activity = execution.getCurrentActivityId();
        if (!availability.enabled()) {
            throw new IllegalStateException("aiPrecheckTask: AI pre-check is disabled on this instance (APP_PRECHECK_ENABLED)");
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        for (String name : ATTRIBUTES) {
            String literal = DeployedActivityParameters.getLiteral(execution, name);
            if (literal != null) {
                attributes.put(name, literal);
            }
        }
        // Défense en profondeur : même validation qu'au déploiement (un BPMN peut arriver sans passer par le parseur).
        BlockAttributeRules.validate(activity, DELEGATE, attributes);
        PrecheckSpec spec = PrecheckSpec.parse(attributes, activity);
        Object dossierId = execution.getVariable("dossierId");
        if (dossierId == null) {
            throw new IllegalStateException("aiPrecheckTask needs a dossier (process variable dossierId)");
        }
        Object linkId = execution.getVariable("dossierWorkflowLinkId");
        RunDossierPrecheckUseCase.WorkflowResult result = useCase.runInWorkflow(UUID.fromString(dossierId.toString()),
                linkId == null ? null : UUID.fromString(linkId.toString()), spec, execution.getVariables());
        if (spec.resultVariable() != null) {
            execution.setVariable(spec.resultVariable(), new LinkedHashMap<>(result.resultVariable()));
        }
        if (result.run().status() == PrecheckRun.Status.FAILED) {
            throw new IllegalStateException("AI pre-check failed: " + result.run().failure()
                    + " (deterministic findings were kept; retry the incident once the cause is fixed)");
        }
    }
}
