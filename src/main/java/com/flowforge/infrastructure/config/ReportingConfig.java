package com.flowforge.infrastructure.config;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.reporting.port.in.GetSlaReportUseCase;
import com.flowforge.domain.reporting.port.in.GetSlaSummaryUseCase;
import com.flowforge.domain.reporting.port.in.GetTenantSlaSettingsUseCase;
import com.flowforge.domain.reporting.port.in.UpsertTenantSlaSettingsUseCase;
import com.flowforge.domain.reporting.port.out.SlaReportQueryPort;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import com.flowforge.domain.reporting.usecase.GetSlaReportUseCaseImpl;
import com.flowforge.domain.reporting.usecase.GetSlaSummaryUseCaseImpl;
import com.flowforge.domain.reporting.usecase.GetTenantSlaSettingsUseCaseImpl;
import com.flowforge.domain.reporting.usecase.UpsertTenantSlaSettingsUseCaseImpl;
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
            TenantSlaSettingsRepository repository, TenantAdminGuard tenantAdminGuard) {
        return new UpsertTenantSlaSettingsUseCaseImpl(repository, tenantAdminGuard, Clock.systemUTC());
    }
}
