package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.notification.port.out.NotificationWebhookRepository;
import be.myrmacta.domain.reporting.port.in.DownloadBiExportUseCase;
import be.myrmacta.domain.reporting.port.in.EvaluateSlaAlertsUseCase;
import be.myrmacta.domain.reporting.port.in.GetOperationalCountsUseCase;
import be.myrmacta.domain.reporting.port.in.GetSlaReportUseCase;
import be.myrmacta.domain.reporting.port.in.GetSlaSummaryUseCase;
import be.myrmacta.domain.reporting.port.in.GetTenantSlaSettingsUseCase;
import be.myrmacta.domain.reporting.port.in.ListBiExportsUseCase;
import be.myrmacta.domain.reporting.port.in.RunBiExportUseCase;
import be.myrmacta.domain.reporting.port.in.UpsertTenantSlaSettingsUseCase;
import be.myrmacta.domain.reporting.port.out.BiExportCommitPort;
import be.myrmacta.domain.reporting.port.out.BiExportContentPort;
import be.myrmacta.domain.reporting.port.out.BiExportFileRepository;
import be.myrmacta.domain.reporting.port.out.OperationalCountsPort;
import be.myrmacta.domain.reporting.port.out.SlaAlertCandidatePort;
import be.myrmacta.domain.reporting.port.out.SlaAlertStateRepository;
import be.myrmacta.domain.reporting.port.out.SlaReportQueryPort;
import be.myrmacta.domain.reporting.port.out.TenantSlaSettingsRepository;
import be.myrmacta.domain.reporting.usecase.DownloadBiExportUseCaseImpl;
import be.myrmacta.domain.reporting.usecase.EvaluateSlaAlertsUseCaseImpl;
import be.myrmacta.domain.reporting.usecase.GetOperationalCountsUseCaseImpl;
import be.myrmacta.domain.reporting.usecase.GetSlaReportUseCaseImpl;
import be.myrmacta.domain.reporting.usecase.GetSlaSummaryUseCaseImpl;
import be.myrmacta.domain.reporting.usecase.GetTenantSlaSettingsUseCaseImpl;
import be.myrmacta.domain.reporting.usecase.ListBiExportsUseCaseImpl;
import be.myrmacta.domain.reporting.usecase.RunBiExportUseCaseImpl;
import be.myrmacta.domain.reporting.usecase.UpsertTenantSlaSettingsUseCaseImpl;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportingConfig {

    @Bean
    GetSlaReportUseCase getSlaReportUseCase(
            SlaReportQueryPort queryPort,
            DossierTypeRepository typeRepository,
            DossierTypeAclRepository aclRepository,
            TenantSlaSettingsRepository slaSettingsRepository) {
        return new GetSlaReportUseCaseImpl(
                queryPort, typeRepository, aclRepository, slaSettingsRepository, Clock.systemUTC());
    }

    @Bean
    GetSlaSummaryUseCase getSlaSummaryUseCase(
            SlaReportQueryPort queryPort,
            DossierTypeRepository typeRepository,
            DossierTypeAclRepository aclRepository) {
        return new GetSlaSummaryUseCaseImpl(queryPort, typeRepository, aclRepository, Clock.systemUTC());
    }

    @Bean
    GetTenantSlaSettingsUseCase getTenantSlaSettingsUseCase(
            TenantSlaSettingsRepository repository, TenantAdminGuard tenantAdminGuard) {
        return new GetTenantSlaSettingsUseCaseImpl(repository, tenantAdminGuard);
    }

    @Bean
    UpsertTenantSlaSettingsUseCase upsertTenantSlaSettingsUseCase(
            TenantSlaSettingsRepository repository,
            TenantAdminGuard tenantAdminGuard,
            NotificationWebhookRepository webhookRepository,
            SlaAlertCandidatePort candidatePort,
            SlaAlertStateRepository alertStateRepository) {
        return new UpsertTenantSlaSettingsUseCaseImpl(
                repository,
                tenantAdminGuard,
                Clock.systemUTC(),
                webhookRepository,
                candidatePort,
                alertStateRepository);
    }

    @Bean
    EvaluateSlaAlertsUseCase evaluateSlaAlertsUseCase(
            TenantSlaSettingsRepository settingsRepository,
            SlaAlertCandidatePort candidatePort,
            SlaAlertStateRepository stateRepository,
            SendNotificationUseCase sendNotificationUseCase,
            NotificationWebhookRepository webhookRepository,
            DossierWorkflowLinkRepository workflowLinkRepository,
            WorkflowParticipantPort participantPort) {
        return new EvaluateSlaAlertsUseCaseImpl(
                settingsRepository,
                candidatePort,
                stateRepository,
                sendNotificationUseCase,
                webhookRepository,
                workflowLinkRepository,
                participantPort);
    }

    @Bean
    RunBiExportUseCase runBiExportUseCase(
            TenantSlaSettingsRepository settingsRepository,
            BiExportContentPort contentPort,
            DocumentStoragePort storagePort,
            BiExportCommitPort commitPort,
            BiExportFileRepository fileRepository) {
        return new RunBiExportUseCaseImpl(settingsRepository, contentPort, storagePort, commitPort, fileRepository);
    }

    @Bean
    GetOperationalCountsUseCase getOperationalCountsUseCase(
            OperationalCountsPort countsPort, DossierTypeAclRepository aclRepository) {
        return new GetOperationalCountsUseCaseImpl(countsPort, aclRepository);
    }

    @Bean
    ListBiExportsUseCase listBiExportsUseCase(
            BiExportFileRepository fileRepository, DossierTypeAclRepository aclRepository) {
        return new ListBiExportsUseCaseImpl(fileRepository, aclRepository);
    }

    @Bean
    DownloadBiExportUseCase downloadBiExportUseCase(
            BiExportFileRepository fileRepository,
            DossierTypeAclRepository aclRepository,
            DocumentStoragePort storagePort) {
        return new DownloadBiExportUseCaseImpl(fileRepository, aclRepository, storagePort);
    }
}
