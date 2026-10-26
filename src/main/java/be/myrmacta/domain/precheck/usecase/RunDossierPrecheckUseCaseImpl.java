package be.myrmacta.domain.precheck.usecase;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.precheck.PrecheckEngine;
import be.myrmacta.domain.precheck.model.FindingKind;
import be.myrmacta.domain.precheck.model.PrecheckOutcome;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import be.myrmacta.domain.precheck.port.in.RunDossierPrecheckUseCase;
import be.myrmacta.domain.precheck.port.out.PrecheckRunRepository;
import be.myrmacta.domain.precheck.port.out.PrecheckVariablesPort;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * TODO §106 (issue #211, lot B) — exécute le moteur et range le résultat. Contexte système : la brique et la file lisent
 * le dossier sans utilisateur ; aucune valeur sensible n'en sort (le moteur ne les transmet jamais).
 */
public final class RunDossierPrecheckUseCaseImpl implements RunDossierPrecheckUseCase {

    static final String SYSTEM_ACTOR = "system";

    private final String tenantId;
    private final DossierRepository dossierRepository;
    private final DossierTypeRepository typeRepository;
    private final DossierElementRepository elementRepository;
    private final PrecheckEngine engine;
    private final PrecheckRunRepository runRepository;
    private final PrecheckVariablesPort variablesPort;
    private final Clock clock;

    public RunDossierPrecheckUseCaseImpl(String tenantId, DossierRepository dossierRepository,
            DossierTypeRepository typeRepository, DossierElementRepository elementRepository, PrecheckEngine engine,
            PrecheckRunRepository runRepository, PrecheckVariablesPort variablesPort, Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository");
        this.variablesPort = Objects.requireNonNull(variablesPort, "variablesPort");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public WorkflowResult runInWorkflow(UUID dossierId, UUID workflowLinkId, PrecheckSpec spec,
            Map<String, Object> variables) {
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(spec, "spec");
        Dossier dossier = dossier(dossierId)
                .orElseThrow(() -> new IllegalStateException("aiPrecheckTask: dossier not found " + dossierId));
        PrecheckOutcome outcome = compute(dossier, spec, variables);
        PrecheckRun run = PrecheckRun.pending(dossierId, PrecheckRun.Trigger.WORKFLOW, SYSTEM_ACTOR, workflowLinkId, spec,
                clock.instant());
        PrecheckRun completed = withAttempt(run).completed(outcome, clock.instant());
        runRepository.insert(completed);
        return new WorkflowResult(completed, resultVariable(completed, outcome));
    }

    @Override
    public PrecheckRun runQueued(PrecheckRun run) {
        Objects.requireNonNull(run, "run");
        PrecheckOutcome outcome;
        Optional<Dossier> dossier = dossier(run.dossierId());
        if (run.attempts() > MAX_QUEUE_ATTEMPTS) {
            outcome = failed("abandoned after " + MAX_QUEUE_ATTEMPTS + " attempts");
        } else if (dossier.isEmpty()) {
            outcome = failed("the dossier no longer exists");
        } else {
            Map<String, Object> variables;
            try {
                variables = variablesPort.currentVariables(run.dossierId());
            } catch (RuntimeException ex) {
                variables = Map.of(); // règles alors « non évaluées »
            }
            outcome = compute(dossier.get(), run.spec(), variables);
        }
        PrecheckRun completed = run.completed(outcome, clock.instant());
        // F4 — rangé seulement si cette tentative détient encore l'exécution ; sinon elle reste à l'autre tentative.
        return runRepository.complete(completed) ? completed : run;
    }

    private PrecheckOutcome compute(Dossier dossier, PrecheckSpec spec, Map<String, Object> variables) {
        DossierType type = typeRepository.findByCode(tenantId, dossier.dossierTypeCode()).orElse(null);
        if (type == null) {
            return failed("the dossier type no longer exists");
        }
        List<DossierElement> elements = elementRepository.findByDossierIdOrderByAddedAtAsc(dossier.id());
        return engine.compute(dossier, type, elements, spec, variables);
    }

    private Optional<Dossier> dossier(UUID dossierId) {
        return dossierRepository.findById(dossierId).filter(d -> tenantId.equals(d.tenantId()));
    }

    private static PrecheckOutcome failed(String failure) {
        return new PrecheckOutcome(PrecheckRun.Status.FAILED, failure, null, null, null, null, null, 0, 0, 0, List.of());
    }

    private static PrecheckRun withAttempt(PrecheckRun run) {
        return new PrecheckRun(run.id(), run.dossierId(), run.trigger(), run.requestedBy(), run.workflowLinkId(),
                run.spec(), run.status(), 1, null, null, null, null, null, null, 0, 0, 0, run.createdAt(), null,
                run.findings());
    }

    private static Map<String, Object> resultVariable(PrecheckRun run, PrecheckOutcome outcome) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", run.status().name());
        result.put("missing", outcome.deterministicCount(FindingKind.MISSING));
        result.put("inconsistent", outcome.deterministicCount(FindingKind.INCONSISTENT));
        result.put("runId", run.id().toString());
        return result;
    }
}
