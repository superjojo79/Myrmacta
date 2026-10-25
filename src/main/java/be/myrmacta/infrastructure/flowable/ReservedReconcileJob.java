package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.dossier.model.AbortReservedReason;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.in.AttachWorkflowUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.infrastructure.config.WorkflowProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ReservedReconcileJob {

    private static final Logger LOG = LoggerFactory.getLogger(ReservedReconcileJob.class);

    private final DossierWorkflowLinkRepository linkRepository;
    private final AttachWorkflowUseCase attachWorkflow;
    private final WorkflowEnginePort workflowEnginePort;
    private final DossierRepository dossierRepository;
    private final WorkflowProperties properties;

    ReservedReconcileJob(
            DossierWorkflowLinkRepository linkRepository,
            AttachWorkflowUseCase attachWorkflow,
            WorkflowEnginePort workflowEnginePort,
            DossierRepository dossierRepository,
            WorkflowProperties properties) {
        this.linkRepository = linkRepository;
        this.attachWorkflow = attachWorkflow;
        this.workflowEnginePort = workflowEnginePort;
        this.dossierRepository = dossierRepository;
        this.properties = properties;
    }

    /** Durée pendant laquelle un lien pris pour réconciliation n'est plus proposé à un autre réconciliateur. */
    static final java.time.Duration RECONCILE_LEASE = java.time.Duration.ofMinutes(2);

    @Scheduled(fixedDelayString = "${app.workflow.reserve-timeout:5m}")
    void reconcileReserved() {
        Instant olderThan = Instant.now().minus(properties.reserveTimeout());
        for (DossierWorkflowLink link : linkRepository.findOrphanReserved(olderThan)) {
            try {
                reconcile(link);
            } catch (RuntimeException ex) {
                // Un lien en échec ne doit pas empêcher la réconciliation des suivants ; il sera repris à l'échéance du bail.
                LOG.warn("Reserved workflow link {} could not be reconciled this pass: {}", link.id(), ex.getMessage());
            }
        }
    }

    void reconcile(DossierWorkflowLink link) {
        // TODO §79 (D5) : prise du lien AVANT de relancer le process — deux réconciliateurs ne démarrent pas deux instances
        // Flowable pour la même business key (Flowable n'impose pas son unicité).
        Instant now = Instant.now();
        if (!linkRepository.claimForReconcile(link.id(), now, now.plus(RECONCILE_LEASE))) {
            return;
        }
        var existing = workflowEnginePort.findProcessInstanceIdByBusinessKey(link.id().toString());
        if (existing.isPresent()) {
            attachWorkflow.confirmStarted(link.id(), existing.get());
            return;
        }
        String processInstanceId;
        try {
            Dossier dossier = dossierRepository.findById(link.dossierId())
                    .orElseThrow(() -> new IllegalStateException("Dossier not found: " + link.dossierId()));
            processInstanceId = workflowEnginePort.start(
                    link.processDefinitionKey(),
                    link.tenantId(),
                    link.id().toString(),
                    WorkflowVariables.withPlatformVariables(null, link, dossier.fields()));
        } catch (RuntimeException ex) {
            attachWorkflow.abortReserved(link.id(), AbortReservedReason.ORPHAN_TIMEOUT);
            return;
        }
        // Le process tourne : si la confirmation échoue (verrou optimiste sur le dossier…), le lien reste RESERVED et le prochain
        // passage retrouve l'instance par business key. On ne l'abandonne JAMAIS ici (avant : lien clos alors que le process tourne).
        attachWorkflow.confirmStarted(link.id(), processInstanceId);
    }
}
