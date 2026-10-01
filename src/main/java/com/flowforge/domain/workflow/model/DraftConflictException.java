package com.flowforge.domain.workflow.model;

/** TODO §82 (D6) — un brouillon d'un autre auteur ou d'une autre provenance existe déjà pour cette clé. */
public class DraftConflictException extends RuntimeException {
    public DraftConflictException(String message) {
        super(message);
    }
}
