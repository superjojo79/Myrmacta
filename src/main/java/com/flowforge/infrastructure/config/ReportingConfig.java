package com.flowforge.infrastructure.config;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.notification.port.out.NotificationWebhookRepository;
import com.flowforge.domain.reporting.port.in.DownloadBiExportUseCase;
import com.flowforge.domain.reporting.port.in.EvaluateSlaAlertsUseCase;
import com.flowforge.domain.reporting.port.in.GetOperationalCountsUseCase;
import com.flowforge.domain.reporting.port.in.GetSlaReportUseCase;
import com.flowforge.domain.reporting.port.in.GetSlaSummaryUseCase;
import com.flowforge.domain.reporting.port.in.GetTenantSlaSettingsUseCase;
import com.flowforge.domain.reporting.port.in.ListBiExportsUseCase;
import com.flowforge.domain.reporting.port.in.RunBiExportUseCase;
import com.flowforge.domain.reporting.port.in.UpsertTenantSlaSettingsUseCase;
import com.flowforge.domain.reporting.port.out.BiExportCommitPort;
import com.flowforge.domain.reporting.port.out.BiExportContentPort;
import com.flowforge.domain.reporting.port.out.BiExportFileRepository;
import com.flowforge.domain.reporting.port.out.OperationalCountsPort;
import com.flowforge.domain.reporting.port.out.SlaAlertCandidatePort;
import com.flowforge.domain.reporting.port.out.SlaAlertStateRepository;
import com.flowforge.domain.reporting.port.out.SlaReportQueryPort;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import com.flowforge.domain.reporting.usecase.DownloadBiExportUseCaseImpl;
import com.flowforge.domain.reporting.usecase.EvaluateSlaAlertsUseCaseImpl;
import com.flowforge.domain.reporting.usecase.GetOperationalCountsUseCaseImpl;
import com.flowforge.domain.reporting.usecase.GetSlaReportUseCaseImpl;
import com.flowforge.domain.reporting.usecase.GetSlaSummaryUseCaseImpl;
import com.flowforge.domain.reporting.usecase.GetTenantSlaSettingsUseCaseImpl;
import com.flowforge.domain.reporting.usecase.ListBiExportsUseCaseImpl;
import com.flowforge.domain.reporting.usecase.RunBiExportUseCaseImpl;
import com.flowforge.domain.reporting.usecase.UpsertTenantSlaSettingsUseCaseImpl;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
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
