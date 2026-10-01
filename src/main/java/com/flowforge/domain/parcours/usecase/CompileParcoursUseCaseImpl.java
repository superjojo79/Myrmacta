package com.flowforge.domain.parcours.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.parcours.ParcoursCompiler;
import com.flowforge.domain.parcours.model.CompiledParcours;
import com.flowforge.domain.parcours.model.Parcours;
import com.flowforge.domain.parcours.model.ParcoursDefinition;
import com.flowforge.domain.parcours.model.ParcoursNotFoundException;
import com.flowforge.domain.parcours.port.in.CompileParcoursUseCase;
import com.flowforge.domain.parcours.port.out.ParcoursRepository;
import com.flowforge.domain.workflow.model.ProposedWorkflowConfig;
import com.flowforge.domain.workflow.model.Provenance;
import com.flowforge.domain.workflow.port.in.ProposeDraftUseCase;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import java.time.Clock;
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
        CompiledParcours compiled = ParcoursCompiler.compile(parcours.key(), parcours.label(), parcours.definition(), this::fieldsOf);
        ProposedWorkflowConfig config = config(parcours.label(), parcours.definition());
        if (dryRun) {
            propose.validateBpmn(actor, parcours.key(), compiled.xml(), config);
            return new Result(compiled, null);
        }
        ProposeDraftUseCase.Proposal proposal = propose.proposeBpmn(actor, provenance, parcours.key(), compiled.xml(), config, replaceDraft);
        repository.markCompiled(tenantId, parcours.key(), clock.instant(), compiled.sha256());
        return new Result(compiled, proposal);
    }

    @Override
    public CompiledParcours preview(AccessContext.User actor, String key, String label, ParcoursDefinition definition) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        return ParcoursCompiler.compile(key, label, definition, this::fieldsOf);
    }

    private static ProposedWorkflowConfig config(String label, ParcoursDefinition definition) {
        return new ProposedWorkflowConfig(label, definition.statuses(), definition.allowedDossierStatuses(),
                definition.onStartDossierStatus(), java.util.List.of());
    }

    private Set<String> fieldsOf(String schemaKey) {
        return schemas.findByKey(tenantId, schemaKey)
                .map(s -> s.fields().stream().map(FieldDefinition::name).collect(Collectors.toSet()))
                .orElse(null);
    }
}
