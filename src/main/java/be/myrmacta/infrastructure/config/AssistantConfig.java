package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.assistant.port.in.ChatWithAssistantUseCase;
import be.myrmacta.domain.assistant.port.in.ManageAssistantActionsUseCase;
import be.myrmacta.domain.assistant.port.in.ManageConversationsUseCase;
import be.myrmacta.domain.assistant.port.out.ConversationRepository;
import be.myrmacta.domain.assistant.port.out.DesignToolsPort;
import be.myrmacta.domain.assistant.port.out.LanguageModelPort;
import be.myrmacta.domain.assistant.usecase.ChatWithAssistantUseCaseImpl;
import be.myrmacta.domain.assistant.usecase.ManageAssistantActionsUseCaseImpl;
import be.myrmacta.domain.assistant.usecase.ManageConversationsUseCaseImpl;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.infrastructure.assistant.AnthropicChatModels;
import be.myrmacta.infrastructure.assistant.DisabledLanguageModel;
import be.myrmacta.infrastructure.assistant.McpDesignToolCatalog;
import be.myrmacta.infrastructure.assistant.SpringAiLanguageModel;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** TODO §86 (LC3) — assistant de conception ; inactif par défaut (aucune requête sortante tant qu'il n'est pas activé). */
@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
class AssistantConfig {

    @Bean
    LanguageModelPort languageModelPort(AssistantProperties properties, ObjectMapper mapper) {
        return properties.enabled() ? new SpringAiLanguageModel(AnthropicChatModels.create(properties), properties.model(), mapper) : new DisabledLanguageModel();
    }

    @Bean
    DesignToolsPort designToolsPort(
            ApplicationContext context, ObjectMapper mapper, be.myrmacta.domain.connector.port.out.ConnectorCatalog connectorCatalog) {
        return new McpDesignToolCatalog(context, mapper, connectorCatalog);
    }

    @Bean
    ChatWithAssistantUseCase chatWithAssistantUseCase(
            TenantProperties tenantProperties,
            TenantAdminGuard guard,
            LanguageModelPort model,
            DesignToolsPort tools,
            ConversationRepository repository,
            AssistantProperties properties) {
        return new ChatWithAssistantUseCaseImpl(
                tenantProperties.id(), Clock.systemUTC(), guard, model, tools, repository, properties.limits());
    }

    @Bean
    ManageAssistantActionsUseCase manageAssistantActionsUseCase(
            TenantProperties tenantProperties, TenantAdminGuard guard, ConversationRepository repository, DesignToolsPort tools) {
        return new ManageAssistantActionsUseCaseImpl(tenantProperties.id(), Clock.systemUTC(), guard, repository, tools);
    }

    @Bean
    ManageConversationsUseCase manageConversationsUseCase(
            TenantProperties tenantProperties, TenantAdminGuard guard, ConversationRepository repository) {
        return new ManageConversationsUseCaseImpl(tenantProperties.id(), guard, repository);
    }
}
