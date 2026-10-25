package be.myrmacta.domain.assistant.model;

/**
 * TODO §86 (D-ASSIST-COST) — plafonds de consommation. Tous sont des garde-fous contre un emballement (boucle d'outils,
 * conversation interminable, coût) et sont configurables.
 */
public record AssistantLimits(
        int maxToolRoundsPerTurn,
        int maxTurnsPerConversation,
        long maxTokensPerConversation,
        long maxTokensPerTenantPerDay,
        int maxOutputTokens,
        int maxMessageChars,
        int maxToolResultCharsToModel,
        int maxStoredToolResultChars) {

    public AssistantLimits {
        if (maxToolRoundsPerTurn < 1 || maxTurnsPerConversation < 1 || maxTokensPerConversation < 1
                || maxTokensPerTenantPerDay < 1 || maxOutputTokens < 1 || maxMessageChars < 1
                || maxToolResultCharsToModel < 1 || maxStoredToolResultChars < 1) {
            throw new IllegalArgumentException("assistant limits must be positive");
        }
    }

    public static AssistantLimits defaults() {
        return new AssistantLimits(12, 60, 400_000L, 3_000_000L, 4096, 8000, 12_000, 4000);
    }
}
