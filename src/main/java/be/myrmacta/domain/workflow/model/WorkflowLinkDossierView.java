package be.myrmacta.domain.workflow.model;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierRelationView;
import java.util.List;
import java.util.Objects;

public record WorkflowLinkDossierView(
        Dossier dossier,
        List<DossierElement> elements,
        List<DossierRelationView> relations
) {
    public WorkflowLinkDossierView {
        Objects.requireNonNull(dossier, "dossier");
        elements = List.copyOf(Objects.requireNonNullElse(elements, List.of()));
        relations = List.copyOf(Objects.requireNonNullElse(relations, List.of()));
    }
}
