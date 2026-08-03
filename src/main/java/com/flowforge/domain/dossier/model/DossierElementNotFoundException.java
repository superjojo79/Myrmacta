package com.flowforge.domain.dossier.model;

import java.util.UUID;

public class DossierElementNotFoundException extends RuntimeException {

    public DossierElementNotFoundException(UUID id) {
        super("Dossier element not found: " + id);
    }
}
