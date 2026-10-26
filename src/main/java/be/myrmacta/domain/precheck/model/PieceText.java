package be.myrmacta.domain.precheck.model;

import java.util.Objects;

/**
 * TODO §106 (lot A, P6) — texte extrait d'une pièce, borné en pages et en caractères. Une pièce sans couche texte (scan,
 * image), chiffrée, illisible ou d'un format non pris en charge donne un statut non lisible, jamais une erreur.
 */
public record PieceText(Status status, String text, int pagesRead, boolean truncated) {

    public enum Status {
        READABLE,
        /** Aucune couche texte : image ou PDF scanné (pas d'OCR). */
        NO_TEXT,
        ENCRYPTED,
        UNREADABLE,
        UNSUPPORTED,
        /** Lecture interrompue : trop complexe pour être lue dans le délai (revue adversariale de #211, F2). */
        TOO_COMPLEX
    }

    public PieceText {
        Objects.requireNonNull(status, "status");
        text = text == null ? "" : text;
        if (status != Status.READABLE && !text.isEmpty()) {
            throw new IllegalArgumentException("only a readable piece carries text");
        }
    }

    public static PieceText unreadable(Status status) {
        return new PieceText(status, "", 0, false);
    }

    public boolean readable() {
        return status == Status.READABLE;
    }
}
