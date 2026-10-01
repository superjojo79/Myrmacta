package com.flowforge.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.Provenance;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

/** TODO §82 (D6, D-MCP-LC-IDENTITY = A) — acteur paramétrable : JWT M2M par défaut, utilisateur de session en portée in-process. */
class McpActorScopeTest {

    private final McpAccessContextFactory factory = new McpAccessContextFactory();
    private static final AccessContext.User ALICE = AccessContext.user("alice", Set.of("TENANT_ADMIN"));
    private static final AccessContext.User BOB = AccessContext.user("bob", Set.of("WORKFLOW_ADMIN"));

    @Test
    void withoutScopeAndWithoutJwtTheCallIsRefused() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(factory::currentActor).isInstanceOf(IllegalStateException.class);
        assertThat(factory.provenance()).isEqualTo(Provenance.MCP);
    }

    @Test
    void theScopedActorIsUsedAndRestoredEvenOnFailure() {
        SecurityContextHolder.clearContext();
        assertThat(McpActorScope.call(ALICE, factory::currentActor)).isSameAs(ALICE);
        assertThat(McpActorScope.call(ALICE, factory::provenance)).isEqualTo(Provenance.ASSISTANT);
        assertThat(McpActorScope.active()).isFalse();

        assertThatThrownBy(() -> McpActorScope.run(ALICE, () -> {
            throw new IllegalArgumentException("boom");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(McpActorScope.active()).isFalse();
    }

    @Test
    void scopesNestAndRestoreTheOuterActor() {
        McpActorScope.run(ALICE, () -> {
            McpActorScope.run(BOB, () -> assertThat(factory.currentActor()).isSameAs(BOB));
            assertThat(factory.currentActor()).isSameAs(ALICE);
        });
        assertThat(McpActorScope.active()).isFalse();
    }

    @Test
    void theScopeIsBoundToTheCurrentThread() throws Exception {
        McpActorScope.run(ALICE, () -> {
            try {
                boolean[] otherThreadActive = new boolean[1];
                Thread t = new Thread(() -> otherThreadActive[0] = McpActorScope.active());
                t.start();
                t.join();
                assertThat(otherThreadActive[0]).isFalse();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
