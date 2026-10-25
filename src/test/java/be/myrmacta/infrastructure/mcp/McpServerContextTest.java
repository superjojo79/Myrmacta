package be.myrmacta.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.support.OidcClientTestConfiguration;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * UT-CTX-1 (D-SDK=A / A-NO-LLM, révisé : D-ASSIST-PROVIDER) : le serveur MCP {@code SYNC} démarre bien, et le starter modèle Anthropic
 * (ajouté pour l'assistant de conception) reste inerte tant que l'assistant est désactivé — aucun bean {@code ChatModel}, donc aucune
 * requête sortante possible.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class McpServerContextTest {

    @Autowired
    private McpSyncServer mcpSyncServer;

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Test
    void mcpSyncServerBeanIsRegistered() {
        assertThat(mcpSyncServer).isNotNull();
    }

    @Test
    void noChatModelBeanWhileTheAssistantIsDisabled() {
        assertThat(context.getBeansOfType(org.springframework.ai.chat.model.ChatModel.class)).isEmpty();
    }
}
