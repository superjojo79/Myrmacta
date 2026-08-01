package com.flowforge.infrastructure.config;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.port.in.CreateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.dossier.port.in.GrantDossierTypeAccessUseCase;
import com.flowforge.domain.dossier.port.in.ListDossierTypesUseCase;
import com.flowforge.domain.dossier.port.in.RevokeDossierTypeAccessUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.out.DossierReferenceGenerator;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.dossier.usecase.CreateDossierTypeUseCaseImpl;
import com.flowforge.domain.dossier.usecase.CreateDossierUseCaseImpl;
import com.flowforge.domain.dossier.usecase.GetDossierTypeUseCaseImpl;
import com.flowforge.domain.dossier.usecase.GetDossierUseCaseImpl;
import com.flowforge.domain.dossier.usecase.GrantDossierTypeAccessUseCaseImpl;
import com.flowforge.domain.dossier.usecase.ListDossierTypesUseCaseImpl;
import com.flowforge.domain.dossier.usecase.RevokeDossierTypeAccessUseCaseImpl;
import com.flowforge.domain.dossier.usecase.UpdateDossierStatusUseCaseImpl;
import com.flowforge.domain.dossier.usecase.UpdateDossierTypeUseCaseImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DossierConfig {

    @Bean
    DossierAccessPolicy dossierAccessPolicy(DossierTypeAclRepository aclRepository) {
        return new DossierAccessPolicy(aclRepository);
    }

    @Bean
    CreateDossierTypeUseCase createDossierTypeUseCase(DossierTypeRepository typeRepository) {
        return new CreateDossierTypeUseCaseImpl(typeRepository);
    }

    @Bean
    UpdateDossierTypeUseCase updateDossierTypeUseCase(DossierTypeRepository typeRepository) {
        return new UpdateDossierTypeUseCaseImpl(typeRepository);
    }

    @Bean
    GetDossierTypeUseCase getDossierTypeUseCase(
            DossierTypeRepository typeRepository,
            DossierAccessPolicy accessPolicy) {
        return new GetDossierTypeUseCaseImpl(typeRepository, accessPolicy);
    }

    @Bean
    ListDossierTypesUseCase listDossierTypesUseCase(
            DossierTypeRepository typeRepository,
            DossierTypeAclRepository aclRepository) {
        return new ListDossierTypesUseCaseImpl(typeRepository, aclRepository);
    }

    @Bean
    GrantDossierTypeAccessUseCase grantDossierTypeAccessUseCase(
            DossierTypeRepository typeRepository,
            DossierTypeAclRepository aclRepository) {
        return new GrantDossierTypeAccessUseCaseImpl(typeRepository, aclRepository);
    }

    @Bean
    RevokeDossierTypeAccessUseCase revokeDossierTypeAccessUseCase(DossierTypeAclRepository aclRepository) {
        return new RevokeDossierTypeAccessUseCaseImpl(aclRepository);
    }

    @Bean
    CreateDossierUseCase createDossierUseCase(
            DossierTypeRepository typeRepository,
            DossierRepository dossierRepository,
            DossierReferenceGenerator referenceGenerator,
            DossierAccessPolicy accessPolicy) {
        return new CreateDossierUseCaseImpl(
                typeRepository, dossierRepository, referenceGenerator, accessPolicy);
    }

    @Bean
    GetDossierUseCase getDossierUseCase(
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy) {
        return new GetDossierUseCaseImpl(dossierRepository, accessPolicy);
    }

    @Bean
    UpdateDossierStatusUseCase updateDossierStatusUseCase(
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy) {
        return new UpdateDossierStatusUseCaseImpl(dossierRepository, accessPolicy);
    }
}
