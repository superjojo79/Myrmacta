package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.parcours.port.in.CompileParcoursUseCase;
import be.myrmacta.domain.parcours.port.in.ManageParcoursUseCase;
import be.myrmacta.domain.parcours.port.out.ParcoursRepository;
import be.myrmacta.domain.parcours.usecase.CompileParcoursUseCaseImpl;
import be.myrmacta.domain.parcours.usecase.ManageParcoursUseCaseImpl;
import be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TODO §88 (LC5) — concepteur de parcours : gestion et compilation en brouillon BPMN. */
@Configuration
class ParcoursConfig {

    @Bean
    ManageParcoursUseCase manageParcoursUseCase(TenantProperties tenantProperties, TenantAdminGuard guard, ParcoursRepository repository) {
        return new ManageParcoursUseCaseImpl(tenantProperties.id(), Clock.systemUTC(), guard, repository);
    }

    @Bean
    CompileParcoursUseCase compileParcoursUseCase(
            TenantProperties tenantProperties,
            TenantAdminGuard guard,
            ParcoursRepository repository,
            ProposeDraftUseCase propose,
            TaskQuestionSchemaRepository schemas) {
        return new CompileParcoursUseCaseImpl(tenantProperties.id(), Clock.systemUTC(), guard, repository, propose, schemas);
    }
}
