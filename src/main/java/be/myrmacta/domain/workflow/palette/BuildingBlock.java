package be.myrmacta.domain.workflow.palette;

import java.util.List;
import java.util.Objects;

/** Brique métier : un élément BPMN pré-configuré, ses paramètres typés et son motif de reconnaissance. */
public record BuildingBlock(
        String id,
        String label,
        String description,
        BlockCategory category,
        BlockElement element,
        BlockMarker marker,
        List<BlockParameter> parameters) {

    public BuildingBlock {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(marker, "marker");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}
