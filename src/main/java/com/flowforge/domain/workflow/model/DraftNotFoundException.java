package com.flowforge.domain.workflow.model;

/** TODO §82 (D6) — aucun brouillon pour cette clé. */
public class DraftNotFoundException extends RuntimeException {
    public DraftNotFoundException(String message) {
        super(message);
    }
}
