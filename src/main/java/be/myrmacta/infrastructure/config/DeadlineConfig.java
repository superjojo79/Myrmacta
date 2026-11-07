package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.deadline.port.out.DeadlineAlertClaims;
import be.myrmacta.domain.deadline.port.out.DeadlineCandidates;
import be.myrmacta.domain.deadline.port.out.DeadlineSettings;
import be.myrmacta.domain.deadline.port.out.IcsCalendarParser;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository;
import be.myrmacta.domain.deadline.port.out.TenantCalendars;
import be.myrmacta.domain.deadline.usecase.EvaluateDeadlinesUseCaseImpl;
import be.myrmacta.domain.deadline.usecase.ManageDeadlineUseCaseImpl;
import be.myrmacta.domain.deadline.usecase.ManageTenantCalendarUseCaseImpl;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.domain.workflow.port.out.WorkflowVisualizationPort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TODO §115 — échéances de dossier : réglage par type (D-DEADLINE-FIELD), rappel et escalade (D-DEADLINE-ESCALATE). */
@Configuration
class DeadlineConfig {

    @Bean
    ManageDeadlineUseCaseImpl manageDeadlineUseCase(
            TenantProperties tenantProperties, DeadlineSettings settings, DossierTypeRepository types, UserGroupsUseCase groups) {
        return new ManageDeadlineUseCaseImpl(tenantProperties.id(), settings, types, groups);
    }

    @Bean
    ManageTenantCalendarUseCaseImpl manageTenantCalendarUseCase(
            TenantProperties tenantProperties, TenantCalendarRepository calendars, TenantAdminGuard guard, IcsCalendarParser icsParser) {
        return new ManageTenantCalendarUseCaseImpl(tenantProperties.id(), calendars, guard, Clock.systemUTC(), icsParser);
    }

    @Bean
    EvaluateDeadlinesUseCaseImpl evaluateDeadlinesUseCase(
            DeadlineSettings settings,
            TenantCalendars calendars,
            DeadlineCandidates candidates,
            DeadlineAlertClaims claims,
            DossierWorkflowLinkRepository links,
            WorkflowParticipantPort participants,
            SendNotificationUseCase notifications,
            UserGroupsUseCase groups,
            WorkflowVisualizationPort engine,
            WorkflowLinkGroupRepository linkGroups,
            DossierElementRepository elements) {
        return new EvaluateDeadlinesUseCaseImpl(settings, calendars, candidates, claims, links, participants, notifications, groups,
                engine, linkGroups, elements);
    }
}
