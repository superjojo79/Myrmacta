package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.mailroom.port.out.ReplyWorkflowSettings;
import be.myrmacta.domain.mailroom.usecase.ManageReplyWorkflowUseCaseImpl;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TODO §113 — Digital Mail Room : workflow « réponse » des types de dossier (D-MAILROOM-REPLY-WORKFLOW). */
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
}
