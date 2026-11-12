package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.DmnXmlVersions;
import be.myrmacta.domain.workflow.draft.ProposedConfigs;
import be.myrmacta.domain.workflow.model.BpmnDraft;
import be.myrmacta.domain.workflow.model.DecisionTableVersionInfo;
import be.myrmacta.domain.workflow.model.DecisionVersionAlreadyCurrentException;
import be.myrmacta.domain.workflow.model.DmnDraft;
import be.myrmacta.domain.workflow.model.DraftArtifactType;
import be.myrmacta.domain.workflow.model.DraftConflictException;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.domain.workflow.model.InvalidDmnException;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.domain.workflow.model.ProposedWorkflowConfig;
import be.myrmacta.domain.workflow.model.UnknownDecisionKeyException;
import be.myrmacta.domain.workflow.model.UnknownDecisionVersionException;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.palette.InactiveBlockReference;
import be.myrmacta.domain.workflow.port.in.DeployBpmnProcessUseCase;
import be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase;
import be.myrmacta.domain.workflow.port.out.BpmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.DmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * TODO §82 (D6) — brouillons venus d'un canal automatisé. Mêmes contrôles qu'un déploiement, aucun effet en ligne : ni
 * déploiement, ni écriture de {@code WorkflowTypeConfig}. Un brouillon d'un autre auteur ou d'une autre provenance n'est jamais
 * écrasé sans demande explicite (D-SOCLE-CONFLICT).
 */
public final class ProposeDraftUseCaseImpl implements ProposeDraftUseCase {

    private final String tenantId;
    private final TenantAdminGuard guard;
    private final BpmnDraftRepository bpmnDrafts;
    private final DmnDraftRepository dmnDrafts;
    private final BpmnParserPort bpmnParser;
    private final DmnParserPort dmnParser;
    private final DeployBpmnProcessUseCase deployBpmn;
    private final WorkflowTypeConfigRepository configRepository;
    private final DmnDeploymentPort dmnDeployment;

    public ProposeDraftUseCaseImpl(
            String tenantId,
            TenantAdminGuard guard,
            BpmnDraftRepository bpmnDrafts,
            DmnDraftRepository dmnDrafts,
            BpmnParserPort bpmnParser,
            DmnParserPort dmnParser,
            DeployBpmnProcessUseCase deployBpmn,
            WorkflowTypeConfigRepository configRepository,
            DmnDeploymentPort dmnDeployment) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.bpmnDrafts = Objects.requireNonNull(bpmnDrafts, "bpmnDrafts");
        this.dmnDrafts = Objects.requireNonNull(dmnDrafts, "dmnDrafts");
        this.bpmnParser = Objects.requireNonNull(bpmnParser, "bpmnParser");
        this.dmnParser = Objects.requireNonNull(dmnParser, "dmnParser");
        this.deployBpmn = Objects.requireNonNull(deployBpmn, "deployBpmn");
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
        this.dmnDeployment = Objects.requireNonNull(dmnDeployment, "dmnDeployment");
    }

    @Override
    public Proposal proposeBpmn(
            AccessContext.User actor,
            Provenance provenance,
            String processDefinitionKey,
            String xml,
            ProposedWorkflowConfig config,
            boolean replaceExisting) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        requireAutomated(provenance);
        List<InactiveBlockReference> inactive = validateBpmn(actor, processDefinitionKey, xml, config);
        Optional<BpmnDraft> existing = bpmnDrafts.findByTenantIdAndProcessDefinitionKey(tenantId, processDefinitionKey);
        boolean replaced = existing.isPresent();
        existing.ifPresent(d -> requireNoForeignDraft(
                d.updatedBy(), d.provenance(), d.updatedAt(), actor, provenance, replaceExisting, "BPMN", processDefinitionKey));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        BpmnDraft saved = bpmnDrafts.save(new BpmnDraft(
                tenantId, processDefinitionKey, xml, now, actor.userId(), provenance, config, null));
        return new Proposal(DraftArtifactType.BPMN, processDefinitionKey, saved.updatedAt(), replaced, inactive);
    }

    @Override
    public List<InactiveBlockReference> validateBpmn(
            AccessContext.User actor, String processDefinitionKey, String xml, ProposedWorkflowConfig config) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        if (xml == null || xml.isBlank()) {
            throw new InvalidBpmnException("xml is required");
        }
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        String extracted = bpmnParser.extractProcessDefinitionKey(bytes);
        if (!extracted.equals(processDefinitionKey)) {
            throw new IllegalArgumentException("processDefinitionKey (" + processDefinitionKey
                    + ") does not match the BPMN process id (" + extracted + ")");
        }
        Optional<WorkflowTypeConfig> existing = configRepository.findByWorkflowKey(tenantId, processDefinitionKey);
        // Même contrôle qu'un déploiement, avec la configuration SOUMISE (sinon l'existante) : une référence absente est refusée ici.
        // TODO §93 D-DRAFT-REF-STATUS : une ressource présente mais pas encore active est acceptée (la publication, stricte, la
        // refusera tant qu'elle ne l'est pas) — un agent peut proposer ensemble un modèle de document et le workflow qui l'utilise.
        Optional<WorkflowTypeConfig> effective = config == null
                ? existing
                : Optional.of(ProposedConfigs.toConfig(tenantId, existing, processDefinitionKey, config, actor.userId()));
        return deployBpmn.validateDraft(bytes, effective);
    }

    @Override
    public Proposal proposeDmn(
            AccessContext.User actor,
            Provenance provenance,
            String decisionKey,
            String xml,
            String note,
            boolean replaceExisting) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        requireAutomated(provenance);
        // TODO §130 (D-DMN-GENERATE-13) : un agent peut encore envoyer du 1.1 — normaliser avant stock.
        String normalized = DmnXmlVersions.to13(xml);
        validateDmn(actor, decisionKey, normalized);
        return saveDmnDraft(actor, provenance, decisionKey, normalized, note, replaceExisting);
    }

    @Override
    public void validateDmn(AccessContext.User actor, String decisionKey, String xml) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        if (xml == null || xml.isBlank()) {
            throw new InvalidDmnException("xml is required");
        }
        // extractDecisionKey valide aussi le document (schéma DMN 1.3, liste blanche JUEL des cellules).
        // TODO §130 : accepter un XML 1.1 en le normalisant (dry-run MCP inclus).
        String normalized = DmnXmlVersions.to13(xml);
        String extracted = dmnParser.extractDecisionKey(normalized.getBytes(StandardCharsets.UTF_8));
        if (!extracted.equals(decisionKey)) {
            throw new IllegalArgumentException(
                    "decisionKey (" + decisionKey + ") does not match the DMN decision id (" + extracted + ")");
        }
    }

    @Override
    public Proposal proposeDmnFromVersion(
            AccessContext.User actor, Provenance provenance, String decisionKey, int version, boolean replaceExisting) {
        guard.requireTenantOrWorkflowAdminOrApiService(actor);
        requireAutomated(provenance);
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        var versions = dmnDeployment.findAllVersions(tenantId, decisionKey);
        if (versions.isEmpty()) {
            throw new UnknownDecisionKeyException("No deployed DMN decision table found for key: " + decisionKey);
        }
        int latest = versions.stream().mapToInt(DecisionTableVersionInfo::version).max().orElseThrow();
        if (version == latest) {
            throw new DecisionVersionAlreadyCurrentException(
                    "Version " + version + " is already the current version of " + decisionKey);
        }
        byte[] xml = dmnDeployment
                .findDeployedXml(tenantId, decisionKey, version)
                .orElseThrow(() -> new UnknownDecisionVersionException(
                        "No deployed version " + version + " for decision key: " + decisionKey));
        String text = DmnXmlVersions.to13(new String(xml, StandardCharsets.UTF_8));
        validateDmn(actor, decisionKey, text);
        return saveDmnDraft(actor, provenance, decisionKey, text, "Remise en ligne de la version " + version, replaceExisting);
    }

    private Proposal saveDmnDraft(
            AccessContext.User actor,
            Provenance provenance,
            String decisionKey,
            String xml,
            String note,
            boolean replaceExisting) {
        Optional<DmnDraft> existing = dmnDrafts.findByTenantIdAndDecisionKey(tenantId, decisionKey);
        boolean replaced = existing.isPresent();
        existing.ifPresent(d -> requireNoForeignDraft(
                d.updatedBy(), d.provenance(), d.updatedAt(), actor, provenance, replaceExisting, "DMN", decisionKey));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        DmnDraft saved = dmnDrafts.save(new DmnDraft(
                tenantId, decisionKey, xml, now, actor.userId(), provenance, trimNote(note), null));
        return new Proposal(DraftArtifactType.DMN, decisionKey, saved.updatedAt(), replaced);
    }

    private static String trimNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String t = note.strip();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }

    private static void requireAutomated(Provenance provenance) {
        if (provenance == null || !provenance.automated()) {
            throw new IllegalArgumentException("a draft proposal comes from MCP or ASSISTANT, not from the editor");
        }
    }

    private static void requireNoForeignDraft(
            String existingAuthor,
            Provenance existingProvenance,
            Instant existingUpdatedAt,
            AccessContext.User actor,
            Provenance provenance,
            boolean replaceExisting,
            String kind,
            String key) {
        boolean sameWriter = existingAuthor.equals(actor.userId()) && existingProvenance == provenance;
        if (!existingProvenance.automated() && !sameWriter) {
            // Revue D6 M3 : le drapeau est fourni par l'agent lui-même (ou par une injection de consignes) : il ne peut pas servir
            // à écraser le travail en cours d'un humain, qui doit d'abord le publier ou l'écarter.
            throw new DraftConflictException("A " + kind + " draft for '" + key + "' is being edited by a human ("
                    + existingAuthor + ", last change " + existingUpdatedAt + "): an agent never replaces it, not even with "
                    + "replaceExistingDraft — ask the human to publish or discard it first");
        }
        if (!sameWriter && !replaceExisting) {
            throw new DraftConflictException("A " + kind + " draft for '" + key + "' already exists, written by "
                    + existingAuthor + " (" + existingProvenance + ") at " + existingUpdatedAt
                    + ": it is not overwritten without an explicit request (replaceExistingDraft=true), or ask a human to "
                    + "publish or discard it first");
        }
    }
}
