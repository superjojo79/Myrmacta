package com.flowforge.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.support.OidcClientTestConfiguration;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * UT-CTX-1 (D-SDK=A / A-NO-LLM) : le starter {@code spring-ai-starter-mcp-server-webmvc} 2.0.0
 * s'active sans tirer de starter chat/model — aucun {@code ChatClient} n'est même chargeable
 * (pas de starter chat dans le classpath), et le serveur MCP {@code SYNC} démarre bien.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class McpServerContextTest {

    @Autowired
    private McpSyncServer mcpSyncServer;

    @Test
    void mcpSyncServerBeanIsRegistered() {
        assertThat(mcpSyncServer).isNotNull();
    }

    @Test
    void noChatClientClassOnClasspath() {
        assertThatThrownBy(() -> Class.forName("org.springframework.ai.chat.client.ChatClient"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
