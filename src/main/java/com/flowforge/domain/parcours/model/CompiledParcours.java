package com.flowforge.domain.parcours.model;

import java.util.List;

/** Résultat d'une compilation : le BPMN, son empreinte, et les avertissements (non bloquants). */
public record CompiledParcours(String xml, String sha256, List<String> warnings, int elements) {
    public CompiledParcours {
        warnings = List.copyOf(warnings);
    }
}
