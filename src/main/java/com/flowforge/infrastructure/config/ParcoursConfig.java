package com.flowforge.infrastructure.config;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.parcours.port.in.CompileParcoursUseCase;
import com.flowforge.domain.parcours.port.in.ManageParcoursUseCase;
import com.flowforge.domain.parcours.port.out.ParcoursRepository;
import com.flowforge.domain.parcours.usecase.CompileParcoursUseCaseImpl;
import com.flowforge.domain.parcours.usecase.ManageParcoursUseCaseImpl;
import com.flowforge.domain.workflow.port.in.ProposeDraftUseCase;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
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
