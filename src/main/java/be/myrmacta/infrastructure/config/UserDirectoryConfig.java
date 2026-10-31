package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.user.port.out.KnownUserRepository;
import be.myrmacta.domain.user.port.out.UserGroupRepository;
import be.myrmacta.domain.user.usecase.RecordUserLoginUseCaseImpl;
import be.myrmacta.domain.user.usecase.UserGroupsUseCaseImpl;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Annuaire local des utilisateurs (D-ASSIGN-DIR) et groupes (TODO §114, D-GROUP-SOURCE). */
@Configuration
class UserDirectoryConfig {

    @Bean
    RecordUserLoginUseCaseImpl recordUserLoginUseCaseImpl(
            TenantProperties tenantProperties, KnownUserRepository knownUserRepository) {
        return new RecordUserLoginUseCaseImpl(tenantProperties.id(), knownUserRepository, Clock.systemUTC());
    }

    @Bean
    UserGroupsUseCaseImpl userGroupsUseCaseImpl(
            TenantProperties tenantProperties, UserGroupRepository userGroupRepository, KnownUserRepository knownUserRepository) {
        return new UserGroupsUseCaseImpl(tenantProperties.id(), userGroupRepository, knownUserRepository);
    }
}
