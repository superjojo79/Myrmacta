package com.flowforge.infrastructure.config;

import com.flowforge.domain.user.port.out.KnownUserRepository;
import com.flowforge.domain.user.usecase.RecordUserLoginUseCaseImpl;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Annuaire local des utilisateurs (D-ASSIGN-DIR). */
@Configuration
class UserDirectoryConfig {

    @Bean
    RecordUserLoginUseCaseImpl recordUserLoginUseCaseImpl(
            TenantProperties tenantProperties, KnownUserRepository knownUserRepository) {
        return new RecordUserLoginUseCaseImpl(tenantProperties.id(), knownUserRepository, Clock.systemUTC());
    }
}
