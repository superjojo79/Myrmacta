package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.mailroom.port.out.ReplyWorkflowSettings;
import be.myrmacta.domain.mailroom.usecase.DepositMailUseCaseImpl;
import be.myrmacta.domain.mailroom.usecase.ManageReplyWorkflowUseCaseImpl;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TODO §113 — Digital Mail Room : workflow « réponse » des types de dossier (D-MAILROOM-REPLY-WORKFLOW), dépôt manuel (D-MAILROOM-DEPOSIT). */
@Configuration
class MailRoomConfig {

    @Bean
    ManageReplyWorkflowUseCaseImpl manageReplyWorkflowUseCase(
            TenantProperties tenantProperties,
            ReplyWorkflowSettings settings,
            DossierTypeRepository types,
            WorkflowTypeConfigRepository workflowConfigs) {
        return new ManageReplyWorkflowUseCaseImpl(tenantProperties.id(), settings, types, workflowConfigs);
    }

    /** TODO §113 (D-MAILROOM-DEPOSIT) — écran « Déposer » : mêmes limites de taille que les pièces jointes des connecteurs d'entrée. */
    @Bean
    DepositMailUseCaseImpl depositMailUseCase(
            TenantProperties tenantProperties,
            ConnectorInstanceRepository instances,
            DossierAccessPolicy accessPolicy,
            ConnectorTriggerRuntime runtime,
            DossierRepository dossiers,
            InboundConnectorProperties inbound) {
        return new DepositMailUseCaseImpl(tenantProperties.id(), instances, accessPolicy, runtime, dossiers, inbound.maxAttachmentBytes());
    }
}
