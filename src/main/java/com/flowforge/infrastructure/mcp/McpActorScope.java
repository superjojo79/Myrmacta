package com.flowforge.infrastructure.mcp;

import com.flowforge.domain.dossier.model.AccessContext;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * TODO §82 (D6, D-MCP-LC-IDENTITY = A) — exécute un outil du catalogue MCP <b>dans le processus</b> avec l'{@link
 * AccessContext} d'un utilisateur de session (assistant de conception, LC3) au lieu du JWT M2M du canal HTTP, qui reste le
 * chemin par défaut et inchangé. Lié au fil courant ; restauré à la sortie, même en cas d'exception. Quand une portée est
 * active, les brouillons écrits portent la provenance {@code ASSISTANT}.
 */
public final class McpActorScope {

    private static final ThreadLocal<AccessContext.User> CURRENT = new ThreadLocal<>();

    private McpActorScope() {}

    public static <T> T call(AccessContext.User actor, Supplier<T> action) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        AccessContext.User previous = CURRENT.get();
        CURRENT.set(actor);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void run(AccessContext.User actor, Runnable action) {
        call(actor, () -> {
            action.run();
            return null;
        });
    }

    /** L'acteur de la portée courante, s'il y en a une. */
    static AccessContext.User current() {
        return CURRENT.get();
    }

    public static boolean active() {
        return CURRENT.get() != null;
    }
}
