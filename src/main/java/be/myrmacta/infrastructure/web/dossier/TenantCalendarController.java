package be.myrmacta.infrastructure.web.dossier;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.in.ManageTenantCalendarUseCase;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §120 (D-CAL-ZONE, D-CAL-WEEKEND) et §121 (D-CAL-ENTRY, D-CAL-API, D-CAL-BE-SOURCE) — calendrier du tenant.
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
