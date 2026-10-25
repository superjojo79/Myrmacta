package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.user.port.out.KnownUserRepository;
import be.myrmacta.domain.user.usecase.RecordUserLoginUseCaseImpl;
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
