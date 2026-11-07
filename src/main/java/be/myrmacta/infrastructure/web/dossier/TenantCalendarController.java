package be.myrmacta.infrastructure.web.dossier;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.DayRange;
import be.myrmacta.domain.deadline.model.SourcePreview;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.in.ManageTenantCalendarUseCase;
import be.myrmacta.domain.deadline.port.out.IcsCalendarParser;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * TODO §120 (D-CAL-ZONE, D-CAL-WEEKEND) et §121 (D-CAL-ENTRY, D-CAL-API, D-CAL-BE-SOURCE, D-CAL-SOURCE, D-CAL-ICS,
 * D-CAL-ICS-PREVIEW) — calendrier du tenant.
 * Réservé à l'administrateur. Sans enregistrement, la lecture rend les défauts (UTC, samedi-dimanche, aucun jour).
 */
@RestController
@RequestMapping("/api/tenant/calendar")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class TenantCalendarController {

    private final ManageTenantCalendarUseCase calendars;
    private final AccessContextFactory access;

    public TenantCalendarController(ManageTenantCalendarUseCase calendars, AccessContextFactory access) {
        this.calendars = calendars;
        this.access = access;
    }

    @GetMapping
    public CalendarResponse get(Authentication authentication) {
        return CalendarResponse.from(calendars.get(access.user(authentication)));
    }

    /**
     * {@code entries} ou l'ancien {@code holidays} (dates ISO, converties en jours fermés d'un jour), jamais les deux.
     * Les sources ne sont pas touchées.
     */
    @PutMapping
    public CalendarResponse replace(@RequestBody CalendarRequest request, Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.entries() != null && request.holidays() != null) {
            throw new IllegalArgumentException("entries and holidays are mutually exclusive");
        }
        List<CalendarEntry> manual = request.entries() != null ? entries(request.entries()) : holidays(request.holidays());
        return CalendarResponse.from(calendars.replace(access.user(authentication), request.zoneId(),
                WeekendPattern.parse(request.weekend()), manual));
    }

    @PostMapping("/belgian-federal-holidays")
    public CalendarResponse addBelgianFederalHolidays(Authentication authentication) {
        return CalendarResponse.from(calendars.loadBelgianFederalHolidays(access.user(authentication)));
    }

    /** Aperçu d'un fichier ICS comme nouvelle source ; n'écrit rien. */
    @PostMapping(value = "/sources/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PreviewResponse previewNewSource(@RequestParam("name") String name, @RequestParam("file") MultipartFile file,
                                            Authentication authentication) {
        return PreviewResponse.from(calendars.previewFileSource(access.user(authentication), name, null, bytes(file)));
    }

    /** Crée la source avec le fichier ; {@code expectedSha256} est celui de l'aperçu. */
    @PostMapping(value = "/sources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CalendarResponse> createSource(@RequestParam("name") String name, @RequestParam("file") MultipartFile file,
                                                         @RequestParam("expectedSha256") String expectedSha256,
                                                         Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CalendarResponse.from(
                calendars.applyFileSource(access.user(authentication), name, null, bytes(file), expectedSha256)));
    }

    @PostMapping(value = "/sources/{id}/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PreviewResponse previewReload(@PathVariable UUID id, @RequestParam("file") MultipartFile file, Authentication authentication) {
        return PreviewResponse.from(calendars.previewFileSource(access.user(authentication), null, id, bytes(file)));
    }

    /** Remplace les jours de la source par ceux du fichier ; ni les autres sources ni les saisies manuelles ne bougent. */
    @PostMapping(value = "/sources/{id}/reload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CalendarResponse reloadSource(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                                         @RequestParam("expectedSha256") String expectedSha256, Authentication authentication) {
        return CalendarResponse.from(calendars.applyFileSource(access.user(authentication), null, id, bytes(file), expectedSha256));
    }

    @DeleteMapping("/sources/{id}")
    public ResponseEntity<Void> deleteSource(@PathVariable UUID id, Authentication authentication) {
        calendars.deleteSource(access.user(authentication), id);
        return ResponseEntity.noContent().build();
    }

    /** Refuse un fichier trop gros avant de le lire en mémoire. */
    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("calendar file is required");
        }
        if (file.getSize() > IcsCalendarParser.MAX_BYTES) {
            throw new IllegalArgumentException("calendar file is over 1 MiB");
        }
        try {
            return file.getBytes();
        } catch (IOException unreadable) {
            throw new IllegalArgumentException("calendar file cannot be read");
        }
    }

    private static List<CalendarEntry> entries(List<EntryDto> raw) {
        List<CalendarEntry> entries = new ArrayList<>();
        for (EntryDto dto : raw) {
            if (dto == null) {
                throw new IllegalArgumentException("entry is required");
            }
            LocalDate start = date(dto.start(), "start");
            LocalDate end = dto.end() == null || dto.end().isBlank() ? start : date(dto.end(), "end");
            entries.add(new CalendarEntry(start, end, CalendarEntry.Nature.parse(dto.nature()), dto.label()));
        }
        return entries;
    }

    private static List<CalendarEntry> holidays(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(value -> CalendarEntry.day(date(value, "holiday"), CalendarEntry.Nature.CLOSED, null)).toList();
    }

    private static LocalDate date(String raw, String field) {
        try {
            return LocalDate.parse(raw == null ? "" : raw.strip());
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException(field + " must be an ISO date");
        }
    }

    record EntryDto(String start, String end, String nature, String label) {

        static EntryDto from(CalendarEntry entry) {
            return new EntryDto(entry.start().toString(), entry.end().toString(), entry.nature().name(), entry.label());
        }
    }

    record RangeDto(String start, String end) {

        static RangeDto from(DayRange range) {
            return new RangeDto(range.start().toString(), range.end().toString());
        }
    }

    /** Ce que l'application du fichier changerait ; {@code sha256} est à renvoyer pour appliquer. */
    record PreviewResponse(UUID sourceId, String name, String kind, List<RangeDto> added, List<RangeDto> removed,
                           List<RangeDto> masked, Map<String, Integer> ignored, String sha256, int periodCount) {

        static PreviewResponse from(SourcePreview preview) {
            Map<String, Integer> ignored = new LinkedHashMap<>();
            preview.ignored().forEach((reason, count) -> ignored.put(reason.name(), count));
            return new PreviewResponse(preview.sourceId(), preview.name(), preview.kind().name(),
                    preview.added().stream().map(RangeDto::from).toList(), preview.removed().stream().map(RangeDto::from).toList(),
                    preview.masked().stream().map(RangeDto::from).toList(), ignored, preview.sha256(), preview.periodCount());
        }
    }

    record CalendarRequest(String zoneId, String weekend, List<EntryDto> entries,
                           @Schema(deprecated = true, description = "Ancien format : dates ISO converties en jours fermés d'un jour. Exclusif avec entries.")
                           List<String> holidays) {}

    record SourceDto(UUID id, String name, String kind, String maskedUrl, int entryCount, Instant loadedAt, String loadedBy,
                     String lastError, List<EntryDto> entries) {

        static SourceDto from(CalendarSource source) {
            return new SourceDto(source.id(), source.name(), source.kind().name(), null, source.closedDays().size(),
                    source.loadedAt(), source.loadedBy(), source.lastError(),
                    source.closedDays().stream().map(EntryDto::from).toList());
        }
    }

    /** {@code holidays} est déprécié : les jours fermés effectifs, calculés (D-CAL-API). */
    record CalendarResponse(String tenantId, String zoneId, String weekend, List<EntryDto> entries, List<SourceDto> sources,
                            @Schema(deprecated = true, description = "Jours fermés effectifs (sources et saisies fermées, moins les jours ouvrés forcés), calculés.")
                            List<String> holidays, Instant updatedAt, String updatedBy) {

        static CalendarResponse from(StoredTenantCalendar stored) {
            TenantCalendar calendar = stored.calendar();
            return new CalendarResponse(stored.tenantId(), calendar.zoneId(), calendar.weekend().name(),
                    calendar.manual().stream().map(EntryDto::from).toList(),
                    calendar.sources().stream().map(SourceDto::from).toList(),
                    calendar.effectiveClosedDays().stream().map(LocalDate::toString).toList(),
                    stored.updatedAt(), stored.updatedBy());
        }
    }
}
