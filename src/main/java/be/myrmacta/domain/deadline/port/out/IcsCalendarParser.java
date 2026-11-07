package be.myrmacta.domain.deadline.port.out;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.IcsIgnoreReason;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * TODO §121 (D-CAL-ICS) — lit un fichier ICS (RFC 5545) et en tire des jours fermés. L'implémentation ne fait aucun
 * appel réseau et borne la taille du fichier et le nombre d'événements.
 */
public interface IcsCalendarParser {

    /** Taille maximale d'un fichier : 1 MiB. */
    int MAX_BYTES = 1024 * 1024;

    /** Nombre maximal de VEVENT d'un fichier. */
    int MAX_EVENTS = 5_000;

    /**
     * @param fromYear première année dont on déroule les récurrences (incluse)
     * @param toYear dernière année dont on déroule les récurrences (incluse)
     * @throws IllegalArgumentException fichier illisible, trop gros ou trop d'événements
     */
    ParsedIcs parse(byte[] content, ZoneId zone, int fromYear, int toYear);

    /** @param closedDays périodes fermées ({@code CLOSED}), triées ; @param sha256 empreinte hexadécimale du contenu */
    record ParsedIcs(List<CalendarEntry> closedDays, Map<IcsIgnoreReason, Integer> ignored, String sha256) {

        public ParsedIcs {
            closedDays = List.copyOf(closedDays);
            ignored = Map.copyOf(ignored);
        }
    }
}
