package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.draft.LineDiff;
import com.flowforge.domain.workflow.draft.ProposedConfigs;
import com.flowforge.domain.workflow.model.BpmnDeploymentResult;
import com.flowforge.domain.workflow.model.BpmnDraft;
import com.flowforge.domain.workflow.model.ConfigPublication;
import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import com.flowforge.domain.workflow.model.DmnDraft;
import com.flowforge.domain.workflow.model.DraftArtifactType;
import com.flowforge.domain.workflow.model.DraftChangedException;
import com.flowforge.domain.workflow.model.DraftNotFoundException;
import com.flowforge.domain.workflow.model.DirectPublicationNotAllowedException;
import com.flowforge.domain.workflow.model.PendingDraft;
import com.flowforge.domain.workflow.model.PublicationMode;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.in.DeployBpmnProcessUseCase;
import com.flowforge.domain.workflow.port.in.DeployDecisionTableUseCase;
import com.flowforge.domain.workflow.port.in.ManageDraftsUseCase;
import com.flowforge.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.out.BpmnDraftRepository;
import com.flowforge.domain.workflow.port.out.ConfigPublicationRepository;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.DmnDraftRepository;
import com.flowforge.domain.workflow.port.out.PublicationPolicy;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * TODO §82 (D6) — l'unique chemin de publication d'un brouillon (écran d'administration et outil MCP).
 *
 * <ul>
 *   <li>on publie <b>exactement le brouillon relu</b> : l'horodatage attendu doit être celui du brouillon courant ;</li>
 *   <li>un acteur {@code API_SERVICE} (agent) ne publie que si la politique de l'instance autorise la publication directe ;</li>
 *   <li>la configuration de workflow proposée est validée <b>avant</b> toute écriture, puis appliquée, puis le diagramme est
 *       déployé — jamais une configuration modifiée sans diagramme en ligne ;</li>
 *   <li>chaque publication est journalisée (rédacteur, provenance, publicateur, version, mode).</li>
 * </ul>
 */
public final class ManageDraftsUseCaseImpl implements ManageDraftsUseCase {

    private static final int MAX_LISTED_PUBLICATIONS = 200;

    private final String tenantId;
    private final TenantAdminGuard guard;
    private final BpmnDraftRepository bpmnDrafts;
    private final DmnDraftRepository dmnDrafts;
    private final DeployBpmnProcessUseCase deployBpmn;
    private final DeployDecisionTableUseCase deployDmn;
    private final SaveWorkflowTypeConfigUseCase saveConfig;
    private final WorkflowTypeConfigRepository configRepository;
    private final WorkflowDeploymentPort workflowDeployment;
    private final DmnDeploymentPort dmnDeployment;
    private final ConfigPublicationRepository publications;
    private final PublicationPolicy policy;

    public ManageDraftsUseCaseImpl(
            String tenantId,
            TenantAdminGuard guard,
            BpmnDraftRepository bpmnDrafts,
            DmnDraftRepository dmnDrafts,
            DeployBpmnProcessUseCase deployBpmn,
            DeployDecisionTableUseCase deployDmn,
            SaveWorkflowTypeConfigUseCase saveConfig,
            WorkflowTypeConfigRepository configRepository,
            WorkflowDeploymentPort workflowDeployment,
            DmnDeploymentPort dmnDeployment,
            ConfigPublicationRepository publications,
            PublicationPolicy policy) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.bpmnDrafts = Objects.requireNonNull(bpmnDrafts, "bpmnDrafts");
        this.dmnDrafts = Objects.requireNonNull(dmnDrafts, "dmnDrafts");
        this.deployBpmn = Objects.requireNonNull(deployBpmn, "deployBpmn");
        this.deployDmn = Objects.requireNonNull(deployDmn, "deployDmn");
        this.saveConfig = Objects.requireNonNull(saveConfig, "saveConfig");
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
        this.workflowDeployment = Objects.requireNonNull(workflowDeployment, "workflowDeployment");
        this.dmnDeployment = Objects.requireNonNull(dmnDeployment, "dmnDeployment");
        this.publications = Objects.requireNonNull(publications, "publications");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public List<PendingDraft> listPending(AccessContext.User actor) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        List<PendingDraft> pending = new ArrayList<>();
        for (BpmnDraft d : bpmnDrafts.findAllByTenantId(tenantId)) {
            var info = workflowDeployment.findDeploymentInfo(tenantId, d.processDefinitionKey());
            var published = workflowDeployment.findDeployedXml(tenantId, d.processDefinitionKey());
            LineDiff.Summary diff = LineDiff.summary(published.map(b -> new String(b, StandardCharsets.UTF_8)).orElse(null), d.xml());
            pending.add(new PendingDraft(DraftArtifactType.BPMN, d.processDefinitionKey(), d.provenance(), d.updatedBy(),
                    d.updatedAt(), d.proposedConfig() != null, null,
                    info.map(i -> i.latestVersion()).orElse(null), diff.added(), diff.removed()));
        }
        for (DmnDraft d : dmnDrafts.findAllByTenantId(tenantId)) {
            var info = dmnDeployment.findDeploymentInfo(tenantId, d.decisionKey());
            var published = dmnDeployment.findDeployedXml(tenantId, d.decisionKey());
            LineDiff.Summary diff = LineDiff.summary(published.map(b -> new String(b, StandardCharsets.UTF_8)).orElse(null), d.xml());
            pending.add(new PendingDraft(DraftArtifactType.DMN, d.decisionKey(), d.provenance(), d.updatedBy(),
                    d.updatedAt(), false, d.note(), info.map(i -> i.version()).orElse(null), diff.added(), diff.removed()));
        }
        pending.sort(Comparator.comparing(PendingDraft::updatedAt).reversed());
        return List.copyOf(pending);
    }

    @Override
    public ConfigPublication publish(
            AccessContext.User actor, DraftArtifactType type, String key, Instant expectedUpdatedAt, PublicationMode mode) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(mode, "mode");
        requireKey(key);
        boolean agent = actor.hasRole("API_SERVICE") && !actor.hasRole("TENANT_ADMIN") && !actor.hasRole("WORKFLOW_ADMIN");
        if ((agent || mode == PublicationMode.DIRECT) && !policy.directPublicationAllowed()) {
            throw new DirectPublicationNotAllowedException("Direct publication is disabled on this instance "
                    + "(app.mcp.publication.mode=draft): a human publishes the draft from the 'Brouillons à publier' screen");
        }
        if (mode == PublicationMode.MANUAL && agent) {
            throw new DirectPublicationNotAllowedException("An agent cannot publish in MANUAL mode");
        }
        if (mode == PublicationMode.MANUAL && expectedUpdatedAt == null) {
            throw new IllegalArgumentException("expectedUpdatedAt is required: publish the draft you reviewed");
        }
        return switch (type) {
            case BPMN -> publishBpmn(actor, key, expectedUpdatedAt, mode);
            case DMN -> publishDmn(actor, key, expectedUpdatedAt, mode);
        };
    }

    private ConfigPublication publishBpmn(AccessContext.User actor, String key, Instant expected, PublicationMode mode) {
        BpmnDraft draft = bpmnDrafts.findByTenantIdAndProcessDefinitionKey(tenantId, key)
                .orElseThrow(() -> new DraftNotFoundException("No BPMN draft for key: " + key));
        requireUnchanged(draft.updatedAt(), expected);
        byte[] xml = draft.xml().getBytes(StandardCharsets.UTF_8);
        Optional<WorkflowTypeConfig> existing = configRepository.findByWorkflowKey(tenantId, key);
        if (draft.proposedConfig() != null) {
            WorkflowTypeConfig config = ProposedConfigs.toConfig(tenantId, existing, key, draft.proposedConfig(), actor.userId());
            // Validation AVANT toute écriture : un refus ne laisse pas une configuration modifiée sans diagramme déployé.
            deployBpmn.validate(xml, Optional.of(config));
            if (existing.isPresent()) {
                saveConfig.update(config, actor);
            } else {
                saveConfig.create(config, actor);
            }
        }
        BpmnDeploymentResult result = deployBpmn.deploy(key + ".bpmn20.xml", xml);
        return record(DraftArtifactType.BPMN, key, result.deployedVersion(), draft.provenance(), draft.updatedBy(), actor, mode);
    }

    private ConfigPublication publishDmn(AccessContext.User actor, String key, Instant expected, PublicationMode mode) {
        DmnDraft draft = dmnDrafts.findByTenantIdAndDecisionKey(tenantId, key)
                .orElseThrow(() -> new DraftNotFoundException("No DMN draft for key: " + key));
        requireUnchanged(draft.updatedAt(), expected);
        DecisionTableDeploymentResult result = deployDmn.deploy(key + ".dmn", draft.xml().getBytes(StandardCharsets.UTF_8));
        return record(DraftArtifactType.DMN, key, result.deployedVersion(), draft.provenance(), draft.updatedBy(), actor, mode);
    }

    private ConfigPublication record(
            DraftArtifactType type,
            String key,
            Integer version,
            com.flowforge.domain.workflow.model.Provenance provenance,
            String author,
            AccessContext.User actor,
            PublicationMode mode) {
        return publications.save(new ConfigPublication(UUID.randomUUID(), tenantId, type, key, version, provenance, author,
                actor.userId(), Instant.now().truncatedTo(ChronoUnit.MICROS), mode));
    }

    private static void requireUnchanged(Instant current, Instant expected) {
        if (expected == null) {
            return; // publication directe d'un agent : pas de relecture humaine à protéger
        }
        if (!current.truncatedTo(ChronoUnit.MICROS).equals(expected.truncatedTo(ChronoUnit.MICROS))) {
            throw new DraftChangedException("The draft changed since you reviewed it (now " + current
                    + "): reload it and review the new version before publishing");
        }
    }

    @Override
    public void discard(AccessContext.User actor, DraftArtifactType type, String key) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        requireKey(key);
        boolean existed = switch (type) {
            case BPMN -> {
                boolean present = bpmnDrafts.findByTenantIdAndProcessDefinitionKey(tenantId, key).isPresent();
                bpmnDrafts.deleteByTenantIdAndProcessDefinitionKey(tenantId, key);
                yield present;
            }
            case DMN -> {
                boolean present = dmnDrafts.findByTenantIdAndDecisionKey(tenantId, key).isPresent();
                dmnDrafts.deleteByTenantIdAndDecisionKey(tenantId, key);
                yield present;
            }
        };
        if (!existed) {
            throw new DraftNotFoundException("No " + type + " draft for key: " + key);
        }
    }

    @Override
    public List<ConfigPublication> recentPublications(AccessContext.User actor, int limit) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        return publications.findRecent(tenantId, Math.max(1, Math.min(limit, MAX_LISTED_PUBLICATIONS)));
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
    }
}
