package com.flowforge.infrastructure.config;

import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import com.flowforge.domain.document.port.out.TemplateSyntaxChecker;
import com.flowforge.domain.document.usecase.ActivateDocumentTemplateUseCaseImpl;
import com.flowforge.domain.document.usecase.ArchiveDocumentTemplateUseCaseImpl;
import com.flowforge.domain.document.usecase.CreateDocumentTemplateUseCaseImpl;
import com.flowforge.domain.document.usecase.GetDocumentTemplateUseCaseImpl;
import com.flowforge.domain.document.usecase.ListDocumentTemplateUseCaseImpl;
import com.flowforge.domain.document.usecase.NewDocumentTemplateVersionUseCaseImpl;
import com.flowforge.domain.document.usecase.ResolveDocumentTemplateUseCaseImpl;
import com.flowforge.domain.document.usecase.UpdateDocumentTemplateUseCaseImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentConfig {

    public static final int MAX_HTML_BYTES = 5 * 1024 * 1024;

    @Bean
    CreateDocumentTemplateUseCaseImpl createDocumentTemplateUseCase(
            TenantProperties tenantProperties, DocumentTemplateRepository repository) {
        return new CreateDocumentTemplateUseCaseImpl(tenantProperties.id(), repository, MAX_HTML_BYTES);
    }

    @Bean
    UpdateDocumentTemplateUseCaseImpl updateDocumentTemplateUseCase(
            TenantProperties tenantProperties, DocumentTemplateRepository repository) {
        return new UpdateDocumentTemplateUseCaseImpl(tenantProperties.id(), repository, MAX_HTML_BYTES);
    }

    @Bean
    ActivateDocumentTemplateUseCaseImpl activateDocumentTemplateUseCase(
            TenantProperties tenantProperties,
            DocumentTemplateRepository repository,
            TemplateSyntaxChecker syntaxChecker) {
        return new ActivateDocumentTemplateUseCaseImpl(tenantProperties.id(), repository, syntaxChecker);
    }

    @Bean
    ArchiveDocumentTemplateUseCaseImpl archiveDocumentTemplateUseCase(
            TenantProperties tenantProperties, DocumentTemplateRepository repository) {
        return new ArchiveDocumentTemplateUseCaseImpl(tenantProperties.id(), repository);
    }

    @Bean
    NewDocumentTemplateVersionUseCaseImpl newDocumentTemplateVersionUseCase(
            TenantProperties tenantProperties, DocumentTemplateRepository repository) {
        return new NewDocumentTemplateVersionUseCaseImpl(tenantProperties.id(), repository);
    }

    @Bean
    ListDocumentTemplateUseCaseImpl listDocumentTemplateUseCase(
            TenantProperties tenantProperties, DocumentTemplateRepository repository) {
        return new ListDocumentTemplateUseCaseImpl(tenantProperties.id(), repository);
    }

    @Bean
    GetDocumentTemplateUseCaseImpl getDocumentTemplateUseCase(
            TenantProperties tenantProperties, DocumentTemplateRepository repository) {
        return new GetDocumentTemplateUseCaseImpl(tenantProperties.id(), repository);
    }

    @Bean
    ResolveDocumentTemplateUseCaseImpl resolveDocumentTemplateUseCase(
            TenantProperties tenantProperties, DocumentTemplateRepository repository) {
        return new ResolveDocumentTemplateUseCaseImpl(tenantProperties.id(), repository);
    }
}
