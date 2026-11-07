package be.myrmacta.domain.deadline.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TODO §121 (D-CAL-ICS-PREVIEW) — ce que l'application d'un contenu changerait, sans rien écrire.
 *
 * @param sourceId la source rechargée, nul pour une création
 * @param added jours fermés que la source n'avait pas
 * @param removed jours fermés que la source n'a plus
 * @param masked jours de la source recouverts par un jour ouvré forcé saisi à la main : ils resteront ouvrés
 * @param ignored événements écartés, par motif
 * @param sha256 empreinte du contenu, à renvoyer à l'application
 * @param periodCount nombre de périodes que la source aurait
 */
public record SourcePreview(UUID sourceId, String name, CalendarSource.Kind kind, List<DayRange> added, List<DayRange> removed,
                            List<DayRange> masked, Map<IcsIgnoreReason, Integer> ignored, String sha256, int periodCount) {

    public SourcePreview {
        added = List.copyOf(added);
        removed = List.copyOf(removed);
        masked = List.copyOf(masked);
        ignored = Map.copyOf(ignored);
    }
}
