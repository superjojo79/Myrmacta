package be.myrmacta.domain.precheck.model;

/**
 * TODO §106 (lot A, C7, H2, H3) — plafonds de la pré-vérification, réglables par instance. Le budget de jetons est
 * distinct de celui de l'assistant de conception.
 */
public record PrecheckLimits(
        long maxTokensPerDay,
        int maxOutputTokens,
        int maxPiecesRead,
        int maxPagesPerPiece,
        int maxCharsPerPiece,
        int maxCharsTotal,
        int maxQuestions) {

    public PrecheckLimits {
        if (maxTokensPerDay < 1 || maxOutputTokens < 1 || maxPiecesRead < 1 || maxPagesPerPiece < 1
                || maxCharsPerPiece < 1 || maxCharsTotal < 1 || maxQuestions < 1) {
            throw new IllegalArgumentException("precheck limits must all be positive");
        }
        if (maxCharsPerPiece > maxCharsTotal) {
            throw new IllegalArgumentException("maxCharsPerPiece must not exceed maxCharsTotal");
        }
    }

    /** Valeurs par défaut de C7. */
    public static PrecheckLimits defaults() {
        return new PrecheckLimits(1_000_000, 2_000, 10, 30, 30_000, 100_000, 20);
    }
}
