package com.flowforge.infrastructure.config;

import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.domain.workflow.portal.PortalParticipantNotifier;
import com.flowforge.domain.workflow.portal.port.in.InvalidatePortalLinksForTaskUseCase;
import com.flowforge.domain.workflow.portal.port.in.IssuePortalLinkUseCase;
import com.flowforge.domain.workflow.portal.port.in.SendPortalInvitesUseCase;
import com.flowforge.domain.workflow.portal.port.out.PortalAvailabilityPort;
import com.flowforge.domain.workflow.portal.port.out.PortalInviteSenderPort;
import com.flowforge.domain.workflow.portal.port.out.PortalLinkRepository;
import com.flowforge.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import com.flowforge.domain.workflow.portal.port.out.PortalTaskLookupPort;
import com.flowforge.domain.workflow.portal.port.out.PortalTokenPort;
import com.flowforge.domain.workflow.portal.usecase.InvalidatePortalLinksForTaskUseCaseImpl;
import com.flowforge.domain.workflow.portal.usecase.IssuePortalLinkUseCaseImpl;
import com.flowforge.domain.workflow.portal.usecase.SendPortalInvitesUseCaseImpl;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.AttachmentScanPort;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.workflow.UserQuestionAnswers;
import com.flowforge.domain.workflow.port.out.TaskQuestionAnswerRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.portal.PortalAccessResolver;
import com.flowforge.domain.workflow.portal.port.in.AddPortalAttachmentUseCase;
import com.flowforge.domain.workflow.portal.port.in.GetPortalTaskUseCase;
import com.flowforge.domain.workflow.portal.port.in.SubmitPortalTaskUseCase;
import com.flowforge.domain.workflow.portal.usecase.AddPortalAttachmentUseCaseImpl;
import com.flowforge.domain.workflow.portal.usecase.GetPortalTaskUseCaseImpl;
import com.flowforge.domain.workflow.portal.usecase.SubmitPortalTaskUseCaseImpl;
import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.workflow.portal.port.in.ExpirePortalLinksUseCase;
import com.flowforge.domain.workflow.portal.port.in.ManagePortalLinkUseCase;
import com.flowforge.domain.workflow.portal.usecase.ExpirePortalLinksUseCaseImpl;
import com.flowforge.domain.workflow.portal.usecase.ManagePortalLinkUseCaseImpl;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** TODO §68 (portail-externe) — câblage des use cases portail (domaine sans annotation Spring). */
@Configuration
@EnableConfigurationProperties(PortalProperties.class)
public class PortalConfig {

    private static final Clock CLOCK = Clock.systemUTC();

    @Bean
    PortalAvailabilityPort portalAvailabilityPort(PortalProperties properties) {
        return new PortalAvailabilityPort() {
            @Override
            public boolean isPortalEnabled() {
                return properties.enabled();
            }

            @Override
            public int maxLinkTtlDays() {
                return properties.maxLinkTtlDays();
            }
        };
    }

    @Bean
    PortalParticipantNotifier portalParticipantNotifier(
            TenantProperties tenantProperties,
            DossierWorkflowLinkRepository workflowLinkRepository,
            @Lazy WorkflowParticipantPort participantPort,
            SendNotificationUseCase sendNotificationUseCase) {
        return new PortalParticipantNotifier(
                tenantProperties.id(), workflowLinkRepository, participantPort, sendNotificationUseCase);
    }

    @Bean
    IssuePortalLinkUseCaseImpl issuePortalLinkUseCase(
            TenantProperties tenantProperties,
            PortalLinkRepository repository,
            TaskQuestionSchemaRepository schemaRepository,
            PortalRecipientResolverPort recipientResolver,
            PortalParticipantNotifier notifier,
            PortalProperties properties) {
        return new IssuePortalLinkUseCaseImpl(tenantProperties.id(), repository, schemaRepository, recipientResolver,
                notifier, CLOCK, properties.defaultLinkTtlDays(), properties.maxLinkTtlDays());
    }

    @Bean
    InvalidatePortalLinksForTaskUseCase invalidatePortalLinksForTaskUseCase(PortalLinkRepository repository) {
        return new InvalidatePortalLinksForTaskUseCaseImpl(repository, CLOCK);
    }

    @Bean
    SendPortalInvitesUseCase sendPortalInvitesUseCase(
            PortalLinkRepository repository,
            PortalTokenPort tokenPort,
            PortalInviteSenderPort senderPort,
            PortalTaskLookupPort taskLookup,
            DossierRepository dossierRepository,
            PortalParticipantNotifier notifier,
            PortalProperties properties) {
        return new SendPortalInvitesUseCaseImpl(repository, tokenPort, senderPort, taskLookup, dossierRepository,
                notifier, CLOCK, properties.send().maxAttempts(), properties.send().batchSize());
    }

    @Bean
    PortalAccessResolver portalAccessResolver(
            PortalLinkRepository repository,
            PortalTokenPort tokenPort,
            PortalTaskLookupPort taskLookup,
            DossierRepository dossierRepository) {
        return new PortalAccessResolver(repository, tokenPort, taskLookup, dossierRepository, CLOCK);
    }

    @Bean
    GetPortalTaskUseCase getPortalTaskUseCase(
            TenantProperties tenantProperties,
            PortalAccessResolver accessResolver,
            TaskQuestionSchemaRepository schemaRepository,
            DossierElementRepository elementRepository) {
        return new GetPortalTaskUseCaseImpl(tenantProperties.id(), accessResolver, schemaRepository, elementRepository);
    }

    @Bean
    AddPortalAttachmentUseCase addPortalAttachmentUseCase(
            TenantProperties tenantProperties,
            PortalAccessResolver accessResolver,
            AttachmentUploadProperties uploadProperties,
            AttachmentScanPort scanPort,
            DocumentStoragePort storagePort,
            AddDossierElementUseCase addDossierElementUseCase) {
        return new AddPortalAttachmentUseCaseImpl(tenantProperties.id(), accessResolver, uploadProperties::isAllowed,
                scanPort, storagePort, addDossierElementUseCase);
    }

    @Bean
    SubmitPortalTaskUseCase submitPortalTaskUseCase(
            TenantProperties tenantProperties,
            PortalAccessResolver accessResolver,
            TaskQuestionSchemaRepository schemaRepository,
            TaskQuestionAnswerRepository answerRepository,
            WorkflowEnginePort workflowEnginePort,
            PortalLinkRepository repository,
            PortalParticipantNotifier notifier) {
        return new SubmitPortalTaskUseCaseImpl(tenantProperties.id(), accessResolver, schemaRepository,
                new UserQuestionAnswers(tenantProperties.id(), answerRepository), workflowEnginePort, repository,
                notifier, CLOCK);
    }

    @Bean
    ExpirePortalLinksUseCase expirePortalLinksUseCase(
            PortalLinkRepository repository, PortalParticipantNotifier notifier, PortalProperties properties) {
        return new ExpirePortalLinksUseCaseImpl(repository, notifier, CLOCK, properties.expiry().batchSize());
    }

    @Bean
    ManagePortalLinkUseCase managePortalLinkUseCase(
            TenantProperties tenantProperties,
            PortalLinkRepository repository,
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy,
            PortalTaskLookupPort taskLookup,
            WorkflowEnginePort workflowEnginePort,
            IssuePortalLinkUseCaseImpl issuer,
            PortalAvailabilityPort availability) {
        return new ManagePortalLinkUseCaseImpl(tenantProperties.id(), repository, dossierRepository, accessPolicy,
                taskLookup, workflowEnginePort, issuer, availability, CLOCK);
    }
}
