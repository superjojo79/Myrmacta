package com.flowforge.infrastructure.config;

import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.domain.workflow.portal.PortalIdentityFieldCheck;
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
import com.flowforge.domain.connector.port.out.ConnectorSecretCipher;
import com.flowforge.domain.dossier.port.in.UpdateDossierFieldsUseCase;
import com.flowforge.domain.workflow.portal.port.in.CompletePortalIdentityUseCase;
import com.flowforge.domain.workflow.portal.port.in.StartPortalIdentityUseCase;
import com.flowforge.domain.workflow.portal.port.out.PortalIdentityProviderPort;
import com.flowforge.domain.workflow.portal.port.out.PortalIdentityRepository;
import com.flowforge.domain.workflow.portal.port.out.PortalSecretCipherPort;
import com.flowforge.domain.workflow.portal.usecase.CompletePortalIdentityUseCaseImpl;
import com.flowforge.domain.workflow.portal.usecase.StartPortalIdentityUseCaseImpl;
import com.flowforge.infrastructure.portal.identity.OidcPortalIdentityProviderAdapter;
import com.flowforge.infrastructure.portal.identity.PortalIdentityKeys;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** TODO §68 (portail-externe) — câblage des use cases portail (domaine sans annotation Spring). */
@Configuration
@EnableConfigurationProperties({PortalProperties.class, PortalStrongAuthProperties.class})
public class PortalConfig {

    private static final Clock CLOCK = Clock.systemUTC();

    @Bean
    PortalAvailabilityPort portalAvailabilityPort(PortalProperties properties, PortalStrongAuthProperties strongAuth) {
        return new PortalAvailabilityPort() {
            @Override
            public boolean isStrongAuthenticationEnabled() {
                return strongAuth.enabled();
            }

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
            PortalProperties properties,
            DossierRepository dossierRepository,
            DossierTypeRepository dossierTypeRepository) {
        return new IssuePortalLinkUseCaseImpl(tenantProperties.id(), repository, schemaRepository, recipientResolver,
                notifier, CLOCK, properties.defaultLinkTtlDays(), properties.maxLinkTtlDays(),
                PortalIdentityFieldCheck.of(tenantProperties.id(), dossierRepository, dossierTypeRepository));
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
            DossierRepository dossierRepository,
            PortalIdentityRepository identities) {
        return new PortalAccessResolver(repository, tokenPort, taskLookup, dossierRepository, CLOCK, identities);
    }

    /** TODO §103 — NRN et vérificateur PKCE chiffrés au repos, avec la clé maîtresse des secrets de connecteur. */
    @Bean
    PortalSecretCipherPort portalSecretCipherPort(TenantProperties tenantProperties, ConnectorSecretCipher cipher) {
        String tenantId = tenantProperties.id();
        return new PortalSecretCipherPort() {
            @Override
            public String encrypt(String plaintext) {
                return cipher.encrypt(tenantId, plaintext);
            }

            @Override
            public String decrypt(String ciphertext) {
                return cipher.decrypt(tenantId, ciphertext);
            }
        };
    }

    /** TODO §103 (T11) — clés de l'instance, lues au démarrage : un fichier absent ou invalide empêche le démarrage. */
    @Bean
    @ConditionalOnProperty(prefix = "app.portal.strong-auth", name = "enabled", havingValue = "true")
    PortalIdentityKeys portalIdentityKeys(PortalStrongAuthProperties strongAuth) {
        return PortalIdentityKeys.load(strongAuth.keys());
    }

    /**
     * TODO §103 (E1, D-BEAUTH-PORTAL-CLIENT) — client OIDC direct de FAS et d'itsme si l'authentification forte est
     * activée (elle suppose le portail), sinon aucun fournisseur. L'URI de retour vient de l'URL publique du portail,
     * jamais de l'en-tête {@code Host}.
     */
    @Bean
    PortalIdentityProviderPort portalIdentityProviderPort(
            PortalStrongAuthProperties strongAuth, PortalProperties portal, ObjectProvider<PortalIdentityKeys> keys) {
        if (!strongAuth.enabled()) {
            return PortalIdentityProviderPort.NONE;
        }
        if (!portal.enabled()) {
            throw new IllegalStateException("app.portal.strong-auth.enabled requires app.portal.enabled=true");
        }
        return new OidcPortalIdentityProviderAdapter(strongAuth, portal.publicBaseUrl() + "/api/portal/identity/callback",
                keys.getObject(), CLOCK);
    }

    @Bean
    StartPortalIdentityUseCase startPortalIdentityUseCase(
            TenantProperties tenantProperties,
            PortalAccessResolver accessResolver,
            PortalIdentityProviderPort providers,
            PortalIdentityRepository identities,
            PortalTokenPort tokenPort,
            PortalSecretCipherPort cipher) {
        return new StartPortalIdentityUseCaseImpl(tenantProperties.id(), accessResolver, providers, identities, tokenPort,
                cipher, CLOCK);
    }

    @Bean
    CompletePortalIdentityUseCase completePortalIdentityUseCase(
            TenantProperties tenantProperties,
            PortalIdentityRepository identities,
            PortalLinkRepository repository,
            PortalTaskLookupPort taskLookup,
            DossierRepository dossierRepository,
            PortalIdentityProviderPort providers,
            PortalTokenPort tokenPort,
            PortalSecretCipherPort cipher,
            PortalStrongAuthProperties strongAuth) {
        return new CompletePortalIdentityUseCaseImpl(tenantProperties.id(), identities, repository, taskLookup,
                dossierRepository, providers, tokenPort, cipher, CLOCK, strongAuth.identityTtl());
    }

    @Bean
    GetPortalTaskUseCase getPortalTaskUseCase(
            TenantProperties tenantProperties,
            PortalAccessResolver accessResolver,
            TaskQuestionSchemaRepository schemaRepository,
            DossierElementRepository elementRepository,
            PortalIdentityProviderPort providers) {
        return new GetPortalTaskUseCaseImpl(tenantProperties.id(), accessResolver, schemaRepository, elementRepository,
                providers);
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
            PortalParticipantNotifier notifier,
            PortalIdentityRepository identities,
            PortalSecretCipherPort cipher,
            UpdateDossierFieldsUseCase updateFields) {
        return new SubmitPortalTaskUseCaseImpl(tenantProperties.id(), accessResolver, schemaRepository,
                new UserQuestionAnswers(tenantProperties.id(), answerRepository), workflowEnginePort, repository,
                notifier, CLOCK, identities, cipher, updateFields);
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
            PortalAvailabilityPort availability,
            PortalIdentityRepository identities) {
        return new ManagePortalLinkUseCaseImpl(tenantProperties.id(), repository, dossierRepository, accessPolicy,
                taskLookup, workflowEnginePort, issuer, availability, CLOCK, identities);
    }
}
