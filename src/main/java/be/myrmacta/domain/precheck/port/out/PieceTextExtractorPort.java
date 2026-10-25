package be.myrmacta.domain.precheck.port.out;

import be.myrmacta.domain.precheck.model.PieceText;

/** TODO §106 (lot A, P6) — texte d'une pièce jointe, borné ; jamais d'exception pour une pièce illisible. */
public interface PieceTextExtractorPort {

    PieceText extract(byte[] content, String contentType, int maxPages, int maxChars);
}
