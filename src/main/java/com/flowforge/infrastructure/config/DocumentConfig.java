package com.flowforge.infrastructure.config;

import com.flowforge.domain.document.port.in.ActivateDocumentTemplateUseCase;
import com.flowforge.domain.document.port.in.GenerateDocumentUseCase;
import com.flowforge.domain.document.port.in.ResolveDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import com.flowforge.domain.document.port.out.HtmlTemplateProcessor;
import com.flowforge.domain.document.port.out.PdfRenderer;
import com.flowforge.domain.document.port.out.TemplateSyntaxChecker;
import com.flowforge.domain.document.usecase.ActivateDocumentTemplateUseCaseImpl;
import com.flowforge.domain.document.usecase.ArchiveDocumentTemplateUseCaseImpl;
import com.flowforge.domain.document.usecase.CreateDocumentTemplateUseCaseImpl;
import com.flowforge.domain.document.usecase.GenerateDocumentUseCaseImpl;
import com.flowforge.domain.document.usecase.GetDocumentTemplateUseCaseImpl;
import com.flowforge.domain.document.usecase.ListDocumentTemplateUseCaseImpl;
import com.flowforge.domain.document.usecase.NewDocumentTemplateVersionUseCaseImpl;
import com.flowforge.domain.document.usecase.ResolveDocumentTemplateUseCaseImpl;
import com.flowforge.domain.document.usecase.UpdateDocumentTemplateUseCaseImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableConfigurationProperties(DocumentGenerationProperties.class)
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

    /**
     * Wraps activate in a single TX (DD-ACTIVATE-TX): archive previous ACTIVE + mark DRAFT ACTIVE.
     */
    @Bean
    ActivateDocumentTemplateUseCase activateDocumentTemplateUseCase(
            TenantProperties tenantProperties,
            DocumentTemplateRepository repository,
            TemplateSyntaxChecker syntaxChecker,
            PlatformTransactionManager transactionManager) {
        ActivateDocumentTemplateUseCaseImpl impl = new ActivateDocumentTemplateUseCaseImpl(
                tenantProperties.id(), repository, syntaxChecker);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return (id, tenantId, updatedBy) ->
                tx.execute(status -> impl.activate(id, tenantId, updatedBy));
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

    @Bean
    GenerateDocumentUseCase generateDocumentUseCase(
            ResolveDocumentTemplateUseCase resolveDocumentTemplateUseCase,
            HtmlTemplateProcessor htmlTemplateProcessor,
            PdfRenderer pdfRenderer) {
        return new GenerateDocumentUseCaseImpl(
                resolveDocumentTemplateUseCase, htmlTemplateProcessor, pdfRenderer);
    }
}
