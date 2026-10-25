package be.myrmacta.domain.parcours.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.parcours.ParcoursCompiler;
import be.myrmacta.domain.parcours.model.CompiledParcours;
import be.myrmacta.domain.parcours.model.Parcours;
import be.myrmacta.domain.parcours.model.ParcoursDefinition;
import be.myrmacta.domain.parcours.model.ParcoursNotFoundException;
import be.myrmacta.domain.parcours.port.in.CompileParcoursUseCase;
import be.myrmacta.domain.parcours.port.out.ParcoursRepository;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.domain.workflow.model.ProposedWorkflowConfig;
import be.myrmacta.domain.workflow.model.UnknownBlockReferenceException;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.domain.workflow.palette.InactiveBlockReference;
import be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TODO §88 (LC5) — compile puis dépose un brouillon BPMN par le chemin commun ({@link ProposeDraftUseCase}) : même parseur, même
 * liste blanche, mêmes contrôles d'existence qu'un déploiement ; rien n'est publié. Les avertissements de variables lisent les champs
 * des schémas de question référencés.
 */
public final class CompileParcoursUseCaseImpl implements CompileParcoursUseCase {

    private final String tenantId;
    private final Clock clock;
    private final TenantAdminGuard guard;
    private final ParcoursRepository repository;
    private final ProposeDraftUseCase propose;
    private final TaskQuestionSchemaRepository schemas;

    public CompileParcoursUseCaseImpl(
            String tenantId,
            Clock clock,
            TenantAdminGuard guard,
            ParcoursRepository repository,
            ProposeDraftUseCase propose,
            TaskQuestionSchemaRepository schemas) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.propose = Objects.requireNonNull(propose, "propose");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
    }

    @Override
    public Result compile(AccessContext.User actor, Provenance provenance, String key, boolean dryRun, boolean replaceDraft) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        Parcours parcours = repository.findByKey(tenantId, key).orElseThrow(() -> new ParcoursNotFoundException(key));
        CompiledParcours compiled = ParcoursCompiler.compile(parcours.key(), parcours.label(), parcours.definition(), this::fieldsOf, this::typesOf);
        ProposedWorkflowConfig config = config(parcours.label(), parcours.definition());
        if (dryRun) {
            return new Result(withInactive(compiled, propose.validateBpmn(actor, parcours.key(), compiled.xml(), config)), null);
        }
        ProposeDraftUseCase.Proposal proposal = propose.proposeBpmn(actor, provenance, parcours.key(), compiled.xml(), config, replaceDraft);
        repository.markCompiled(tenantId, parcours.key(), clock.instant(), compiled.sha256());
        return new Result(withInactive(compiled, proposal.inactiveReferences()), proposal);
    }

    @Override
    public Preview preview(AccessContext.User actor, String key, String label, ParcoursDefinition definition) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        CompiledParcours compiled = ParcoursCompiler.compile(key, label, definition, this::fieldsOf, this::typesOf);
        try {
            return new Preview(withInactive(compiled, propose.validateBpmn(actor, key, compiled.xml(), config(label, definition))), null);
        } catch (InvalidBpmnException | UnknownBlockReferenceException ex) {
            return new Preview(compiled, ex.getMessage());
        }
    }

    /**
     * TODO §93 D-DRAFT-REF-STATUS — une ressource référencée présente mais inactive n'empêche pas le brouillon : elle devient un
     * avertissement, car la publication la refusera tant qu'elle n'est pas activée. L'empreinte reste celle du BPMN.
     */
    private static CompiledParcours withInactive(CompiledParcours compiled, List<InactiveBlockReference> inactive) {
        if (inactive.isEmpty()) {
            return compiled;
        }
        List<String> warnings = new ArrayList<>(compiled.warnings());
        for (InactiveBlockReference ref : inactive) {
            warnings.add("step " + ref.activityId() + ": " + ref.parameter() + " '" + ref.value()
                    + "' exists but is not active yet; publishing the draft will be refused until it is activated");
        }
        return new CompiledParcours(compiled.xml(), compiled.sha256(), warnings, compiled.elements());
    }

    private static ProposedWorkflowConfig config(String label, ParcoursDefinition definition) {
        return new ProposedWorkflowConfig(label, definition.statuses(), definition.allowedDossierStatuses(),
                definition.onStartDossierStatus(), java.util.List.of());
    }

    private java.util.Map<String, be.myrmacta.domain.dossier.model.FieldType> typesOf(String schemaKey) {
        return schemas.findByKey(tenantId, schemaKey)
                .map(s -> s.fields().stream().collect(Collectors.toMap(FieldDefinition::name, FieldDefinition::type, (a, b) -> a)))
                .orElse(null);
    }

    private Set<String> fieldsOf(String schemaKey) {
        return schemas.findByKey(tenantId, schemaKey)
                .map(s -> s.fields().stream().map(FieldDefinition::name).collect(Collectors.toSet()))
                .orElse(null);
    }
}
