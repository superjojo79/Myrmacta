package be.myrmacta.infrastructure.flowable;

import be.myrmacta.application.workflow.ManualStartOrchestrator;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.DuplicateDossierRelationException;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.RelationType;
import be.myrmacta.domain.dossier.port.in.UpdateDossierFieldsUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierStatusUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.user.model.UserGroupNotFoundException;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.dossier.ChildDossierRelationConfirmer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TODO §112 (D-MAILROOM-ROUTE) — brique « Router le courrier ». Lit la décision de la table de routage (variables
 * {@code routageAction}, {@code routageService}, {@code routageTypeDossier}, {@code routageProcessus},
 * {@code routageDelaiJours}) et :
 *
 * <ul>
 *   <li>{@code OUVRIR} : crée le dossier métier lié au courrier ({@code LINKED}), y démarre le process avec le groupe
 *       destinataire (D-GROUP-LINK), renseigne {@code service} et {@code echeance} du courrier ;
 *   <li>sinon, ou si une valeur est hors catalogue : envoie le courrier à la file de tri ({@code groupeTri}, paramètre
 *       littéral de la brique, devient le groupe du lien de tri). Une erreur de configuration n'arrête jamais le courrier.
 * </ul>
 *
 * Toutes les vérifications ont lieu AVANT la première écriture : une exception remontée par une écriture transactionnelle
 * condamnerait la transaction du moteur, qu'un repli ne pourrait plus sauver.
 */
@Component("routeMailTask")
class RouteMailDelegate implements JavaDelegate {

    static final String ACTION = "routageAction";
    static final String SERVICE = "routageService";
    static final String TYPE = "routageTypeDossier";
    static final String PROCESS = "routageProcessus";
    static final String DELAY_DAYS = "routageDelaiJours";
    static final String SORTING_GROUP = "groupeTri";
    static final String RESULT = "routageResultat";
    static final String REASON = "routageMotif";
    static final String BUSINESS_DOSSIER_ID = "dossierMetierId";
    static final String BUSINESS_DOSSIER_REFERENCE = "dossierMetierReference";
    static final int MAX_DELAY_DAYS = 3_650;
    static final int MAX_LABEL_CHARS = 200;

    private static final Logger log = LoggerFactory.getLogger(RouteMailDelegate.class);
    private static final AccessContext SYSTEM = AccessContext.system("flowable-delegate");

    private final ChildDossierRelationConfirmer confirmer;
    private final ManualStartOrchestrator orchestrator;
    private final DossierRepository dossierRepository;
    private final DossierTypeRepository typeRepository;
    private final WorkflowDeploymentPort deploymentPort;
    private final UserGroupsUseCase groups;
    private final WorkflowLinkGroupRepository linkGroups;
    private final UpdateDossierFieldsUseCase updateFields;
    private final UpdateDossierStatusUseCase updateStatus;
    private final TenantProperties tenantProperties;
    private final Clock clock;

    @Autowired
    RouteMailDelegate(
            ChildDossierRelationConfirmer confirmer,
            ManualStartOrchestrator orchestrator,
            DossierRepository dossierRepository,
            DossierTypeRepository typeRepository,
            WorkflowDeploymentPort deploymentPort,
            UserGroupsUseCase groups,
            WorkflowLinkGroupRepository linkGroups,
            UpdateDossierFieldsUseCase updateFields,
            UpdateDossierStatusUseCase updateStatus,
            TenantProperties tenantProperties) {
        this(confirmer, orchestrator, dossierRepository, typeRepository, deploymentPort, groups, linkGroups, updateFields,
                updateStatus, tenantProperties, Clock.systemUTC());
    }

    RouteMailDelegate(
            ChildDossierRelationConfirmer confirmer,
            ManualStartOrchestrator orchestrator,
            DossierRepository dossierRepository,
            DossierTypeRepository typeRepository,
            WorkflowDeploymentPort deploymentPort,
            UserGroupsUseCase groups,
            WorkflowLinkGroupRepository linkGroups,
            UpdateDossierFieldsUseCase updateFields,
            UpdateDossierStatusUseCase updateStatus,
            TenantProperties tenantProperties,
            Clock clock) {
        this.confirmer = Objects.requireNonNull(confirmer, "confirmer");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.deploymentPort = Objects.requireNonNull(deploymentPort, "deploymentPort");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.linkGroups = Objects.requireNonNull(linkGroups, "linkGroups");
        this.updateFields = Objects.requireNonNull(updateFields, "updateFields");
        this.updateStatus = Objects.requireNonNull(updateStatus, "updateStatus");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void execute(DelegateExecution execution) {
        UUID mailId = UUID.fromString(String.valueOf(execution.getVariable(WorkflowVariables.DOSSIER_ID)));
        Dossier mail = dossierRepository.findById(mailId)
                .orElseThrow(() -> new IllegalStateException("mail dossier not found: " + mailId));
        DossierType mailType = typeRepository.findByCode(tenantProperties.id(), mail.dossierTypeCode())
                .orElseThrow(() -> new IllegalStateException("mail dossier type not found: " + mail.dossierTypeCode()));
        Plan plan = plan(execution, mail);
        if (plan.refusal() != null) {
            toSorting(execution, mailType, mail, plan.refusal());
            return;
        }
        String idempotencyKey = execution.getProcessInstanceId() + ":" + execution.getCurrentActivityId();
        UUID businessId = confirmWithRetryOnRace(mailId, plan, idempotencyKey);
        openDraftIfNeeded(businessId);
        orchestrator.start(businessId, plan.process(), Map.of(), idempotencyKey, "workflow", plan.group());
        Dossier business = dossierRepository.findById(businessId)
                .orElseThrow(() -> new IllegalStateException("business dossier not found: " + businessId));
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("service", plan.group());
        if (plan.delayDays() != null) {
            patch.put("echeance", LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).plusDays(plan.delayDays()).toString());
        }
        patchDeclaredFields(mailType, mail, patch);
        execution.setVariable(RESULT, "ROUTE");
        execution.setVariable(BUSINESS_DOSSIER_ID, businessId.toString());
        execution.setVariable(BUSINESS_DOSSIER_REFERENCE, business.reference());
    }

    /** Décision vérifiée contre les catalogues, sans aucune écriture. */
    private Plan plan(DelegateExecution execution, Dossier mail) {
        String action = text(execution.getVariable(ACTION));
        if (!"OUVRIR".equals(action == null ? null : action.toUpperCase(Locale.ROOT))) {
            return Plan.refused(action == null ? "aucune action de routage" : "action " + action);
        }
        String group = text(execution.getVariable(SERVICE));
        if (group == null || !groupExists(group)) {
            return Plan.refused("groupe inconnu : " + group);
        }
        String typeCode = text(execution.getVariable(TYPE));
        Optional<DossierType> type = typeCode == null ? Optional.empty() : typeRepository.findByCode(tenantProperties.id(), typeCode);
        if (type.isEmpty() || !type.get().active()) {
            return Plan.refused("type de dossier inconnu ou inactif : " + typeCode);
        }
        String process = text(execution.getVariable(PROCESS));
        if (process == null || deploymentPort.findDeploymentInfo(tenantProperties.id(), process).isEmpty()) {
            return Plan.refused("process non déployé : " + process);
        }
        Integer delay = delayDays(execution.getVariable(DELAY_DAYS));
        if (execution.getVariable(DELAY_DAYS) != null && delay == null) {
            return Plan.refused("délai invalide : " + execution.getVariable(DELAY_DAYS));
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldDefinition definition : type.get().fieldDefinitions()) {
            if (mail.fields().containsKey(definition.name())) {
                fields.put(definition.name(), mail.fields().get(definition.name()));
            }
        }
        for (FieldDefinition definition : type.get().fieldDefinitions()) {
            boolean unconditional = definition.requiredWhen() == null || definition.requiredWhen().isBlank();
            if (definition.required() && unconditional && definition.defaultValue() == null && !fields.containsKey(definition.name())) {
                return Plan.refused("champ obligatoire du type " + typeCode + " absent du courrier : " + definition.name());
            }
        }
        return new Plan(group, typeCode, process, delay, fields, label(mail), null);
    }

    private void toSorting(DelegateExecution execution, DossierType mailType, Dossier mail, String reason) {
        String sortingGroup = text(DeployedActivityParameters.getLiteral(execution, SORTING_GROUP));
        Object linkId = execution.getVariable(WorkflowVariables.DOSSIER_WORKFLOW_LINK_ID);
        if (sortingGroup != null && groupExists(sortingGroup) && linkId != null) {
            linkGroups.assign(tenantProperties.id(), UUID.fromString(linkId.toString()), sortingGroup);
            patchDeclaredFields(mailType, mail, Map.of("service", sortingGroup));
        } else {
            log.warn("event=mail_sorting_group_missing dossierId={} group={}", mail.id(), sortingGroup);
        }
        execution.setVariable(RESULT, "A_TRIER");
        execution.setVariable(REASON, reason);
    }

    /** N'écrit que les champs que le type du courrier déclare : la brique ne dépend pas d'un schéma précis. */
    private void patchDeclaredFields(DossierType mailType, Dossier mail, Map<String, Object> patch) {
        Map<String, Object> declared = new LinkedHashMap<>();
        for (FieldDefinition definition : mailType.fieldDefinitions()) {
            if (patch.containsKey(definition.name())) {
                declared.put(definition.name(), patch.get(definition.name()));
            }
        }
        if (!declared.isEmpty()) {
            updateFields.update(new UpdateDossierFieldsUseCase.UpdateDossierFieldsCommand(mail.id(), declared, "workflow", null), SYSTEM);
        }
    }

    private boolean groupExists(String code) {
        try {
            groups.get(code);
            return true;
        } catch (UserGroupNotFoundException | IllegalArgumentException unknown) {
            return false;
        }
    }

    private UUID confirmWithRetryOnRace(UUID mailId, Plan plan, String idempotencyKey) {
        try {
            return confirmer.confirm(tenantProperties.id(), mailId, plan.type(), plan.label(), plan.fields(), RelationType.LINKED,
                    idempotencyKey);
        } catch (DuplicateDossierRelationException race) {
            return confirmer.confirm(tenantProperties.id(), mailId, plan.type(), plan.label(), plan.fields(), RelationType.LINKED,
                    idempotencyKey);
        }
    }

    private void openDraftIfNeeded(UUID dossierId) {
        Dossier dossier = dossierRepository.findById(dossierId).orElse(null);
        if (dossier != null && dossier.status() == DossierStatus.DRAFT) {
            updateStatus.update(new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(dossierId, DossierStatus.OUVERT, "workflow", null),
                    SYSTEM);
        }
    }

    private static String label(Dossier mail) {
        String subject = text(mail.fields().get("objet"));
        String label = subject == null ? mail.reference() : subject;
        return label.length() > MAX_LABEL_CHARS ? label.substring(0, MAX_LABEL_CHARS) : label;
    }

    private static Integer delayDays(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            int days = raw instanceof Number number ? Math.toIntExact(number.longValue()) : Integer.parseInt(raw.toString().strip());
            return days >= 0 && days <= MAX_DELAY_DAYS ? days : null;
        } catch (ArithmeticException | NumberFormatException invalid) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().strip();
    }

    private record Plan(String group, String type, String process, Integer delayDays, Map<String, Object> fields, String label,
            String refusal) {

        static Plan refused(String reason) {
            return new Plan(null, null, null, null, Map.of(), null, reason);
        }
    }
}
