package com.flowforge.domain.workflow.model;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import java.util.List;
import java.util.Objects;

public record WorkflowLinkDossierView(
        Dossier dossier,
        List<DossierElement> elements,
        List<Object> relations
) {
    public WorkflowLinkDossierView {
        Objects.requireNonNull(dossier, "dossier");
        elements = List.copyOf(Objects.requireNonNullElse(elements, List.of()));
        relations = List.copyOf(Objects.requireNonNullElse(relations, List.of()));
    }
}
