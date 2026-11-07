package be.myrmacta.infrastructure.web.dossier;

import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.in.ManageTenantCalendarUseCase;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §120 (D-CAL-ZONE, D-CAL-WEEKEND, D-CAL-HOLIDAYS) — calendrier du tenant. Réservé à l'administrateur.
 * Sans enregistrement, la lecture rend les défauts (UTC, samedi-dimanche, aucun férié).
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

    @PutMapping
    public CalendarResponse replace(@RequestBody CalendarRequest request, Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        List<LocalDate> holidays = dates(request.holidays());
        return CalendarResponse.from(calendars.replace(access.user(authentication), request.zoneId(),
                WeekendPattern.parse(request.weekend()), holidays));
    }

    @PostMapping("/belgian-federal-holidays")
    public CalendarResponse addBelgianFederalHolidays(Authentication authentication) {
        return CalendarResponse.from(calendars.addBelgianFederalHolidays(access.user(authentication)));
    }

    private static List<LocalDate> dates(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        try {
            return raw.stream().map(LocalDate::parse).toList();
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("holiday must be an ISO date");
        }
    }

    record CalendarRequest(String zoneId, String weekend, List<String> holidays) {}

    record CalendarResponse(String tenantId, String zoneId, String weekend, List<String> holidays, Instant updatedAt, String updatedBy) {

        static CalendarResponse from(StoredTenantCalendar stored) {
            TenantCalendar calendar = stored.calendar();
            return new CalendarResponse(stored.tenantId(), calendar.zoneId(), calendar.weekend().name(),
                    calendar.holidays().stream().map(LocalDate::toString).toList(), stored.updatedAt(), stored.updatedBy());
        }
    }
}
