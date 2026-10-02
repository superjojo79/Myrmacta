package com.flowforge.domain.promotion.model;

/** TODO §83 (D4) — bundle illisible, d'un format inconnu, altéré ou hors périmètre : 400, rien n'est écrit. */
public class InvalidBundleException extends IllegalArgumentException {
    public InvalidBundleException(String message) {
        super(message);
    }

    public InvalidBundleException(String message, Throwable cause) {
        super(message, cause);
    }
}
