package be.myrmacta.domain.deadline.usecase;

import be.myrmacta.domain.deadline.model.BelgianFederalHolidays;
import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.CalendarSourceConflictException;
import be.myrmacta.domain.deadline.model.CalendarSourceNotFoundException;
import be.myrmacta.domain.deadline.model.DayRange;
import be.myrmacta.domain.deadline.model.SourcePreview;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.in.ManageTenantCalendarUseCase;
import be.myrmacta.domain.deadline.port.out.IcsCalendarParser;
import be.myrmacta.domain.deadline.port.out.IcsCalendarParser.ParsedIcs;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** TODO §120 et §121 — le calendrier lu sans ligne en base est le défaut, non persisté tant qu'on ne l'enregistre pas. */
public final class ManageTenantCalendarUseCaseImpl implements ManageTenantCalendarUseCase {

    /** Nom de la source des fériés fédéraux belges (D-CAL-BE-SOURCE). */
    public static final String BELGIAN_SOURCE_NAME = "Fériés fédéraux belges";

    private final String tenantId;
    private final TenantCalendarRepository repository;
    private final TenantAdminGuard guard;
    private final Clock clock;
    private final IcsCalendarParser parser;

    public ManageTenantCalendarUseCaseImpl(String tenantId, TenantCalendarRepository repository, TenantAdminGuard guard, Clock clock,
                                           IcsCalendarParser parser) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    @Override
    public StoredTenantCalendar get(AccessContext.User actor) {
        guard.requireTenantAdmin(actor);
        return repository.find(tenantId).orElseGet(this::defaults);
    }

    @Override
    public StoredTenantCalendar replace(AccessContext.User actor, String zoneId, WeekendPattern weekend, List<CalendarEntry> manual) {
        guard.requireTenantAdmin(actor);
        TenantCalendar next = current().withSettings(zoneId, weekend, manual);
        return saveSettings(next, actor);
    }

    @Override
    public StoredTenantCalendar loadBelgianFederalHolidays(AccessContext.User actor) {
        guard.requireTenantAdmin(actor);
        TenantCalendar current = current();
        Instant now = clock.instant();
        int year = current.toCalendar().today(now).getYear();
        List<CalendarEntry> days = BelgianFederalHolidays.entries(year, year + 1);
        CalendarSource source = current.sources().stream()
                .filter(existing -> existing.kind() == CalendarSource.Kind.BE_FEDERAL)
                .findFirst()
                .map(existing -> existing.reloaded(days, now, actor.userId(), null))
                .orElseGet(() -> CalendarSource.create(BELGIAN_SOURCE_NAME, CalendarSource.Kind.BE_FEDERAL, days, now,
                        actor.userId(), null));
        TenantCalendar next = current.withSource(source);
        StoredTenantCalendar stored = saveSettings(next, actor);
        repository.saveSource(tenantId, source);
        return stored;
    }

    @Override
    public SourcePreview previewFileSource(AccessContext.User actor, String name, UUID sourceId, byte[] content) {
        guard.requireTenantAdmin(actor);
        return plan(current(), actor, name, sourceId, content).preview();
    }

    @Override
    public StoredTenantCalendar applyFileSource(AccessContext.User actor, String name, UUID sourceId, byte[] content,
                                                String expectedSha256) {
        guard.requireTenantAdmin(actor);
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            throw new IllegalArgumentException("expectedSha256 is required");
        }
        TenantCalendar current = current();
        Plan plan = plan(current, actor, name, sourceId, content);
        if (!plan.preview().sha256().equalsIgnoreCase(expectedSha256.strip())) {
            throw new CalendarSourceConflictException("The content changed since the preview: preview it again");
        }
        StoredTenantCalendar stored = saveSettings(plan.calendar(), actor);
        repository.saveSource(tenantId, plan.source());
        return stored;
    }

    @Override
    public void deleteSource(AccessContext.User actor, UUID sourceId) {
        guard.requireTenantAdmin(actor);
        if (current().sources().stream().noneMatch(source -> source.id().equals(sourceId))
                || !repository.deleteSource(tenantId, sourceId)) {
            throw new CalendarSourceNotFoundException(sourceId);
        }
    }

    /** Ce que donnerait le contenu : la source qui en résulte, le calendrier avec elle, et l'aperçu des différences. */
    private Plan plan(TenantCalendar current, AccessContext.User actor, String name, UUID sourceId, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("calendar file is required");
        }
        CalendarSource existing = null;
        if (sourceId != null) {
            existing = current.sources().stream().filter(source -> source.id().equals(sourceId)).findFirst()
                    .orElseThrow(() -> new CalendarSourceNotFoundException(sourceId));
            if (existing.kind() != CalendarSource.Kind.ICS_FILE) {
                throw new IllegalArgumentException("a " + existing.kind() + " source cannot be reloaded from a file");
            }
        } else {
            String wanted = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
            if (current.sources().stream().anyMatch(source -> source.nameKey().equals(wanted))) {
                throw new CalendarSourceConflictException("A source is already named: " + name.strip());
            }
        }
        Instant now = clock.instant();
        int year = current.toCalendar().today(now).getYear();
        ParsedIcs parsed = parser.parse(content, java.time.ZoneId.of(current.zoneId()), year - 1, year + 2);
        CalendarSource source = existing == null
                ? CalendarSource.create(name, CalendarSource.Kind.ICS_FILE, parsed.closedDays(), now, actor.userId(), parsed.sha256())
                : existing.reloaded(parsed.closedDays(), now, actor.userId(), parsed.sha256());
        TenantCalendar next = current.withSource(source);
        Set<LocalDate> before = existing == null ? Set.of() : days(existing);
        Set<LocalDate> after = days(source);
        Set<LocalDate> added = new HashSet<>(after);
        added.removeAll(before);
        Set<LocalDate> removed = new HashSet<>(before);
        removed.removeAll(after);
        Set<LocalDate> forcedOpen = next.toCalendar().forcedOpen();
        Set<LocalDate> masked = new HashSet<>(after);
        masked.retainAll(forcedOpen);
        SourcePreview preview = new SourcePreview(source.id(), source.name(), source.kind(), DayRange.compress(added),
                DayRange.compress(removed), DayRange.compress(masked), parsed.ignored(), parsed.sha256(), source.closedDays().size());
        return new Plan(source, next, preview);
    }

    private static Set<LocalDate> days(CalendarSource source) {
        Set<LocalDate> days = new HashSet<>();
        source.closedDays().forEach(entry -> days.addAll(entry.days()));
        return days;
    }

    private record Plan(CalendarSource source, TenantCalendar calendar, SourcePreview preview) {}

    private TenantCalendar current() {
        return repository.find(tenantId).map(StoredTenantCalendar::calendar).orElseGet(TenantCalendar::defaults);
    }

    private StoredTenantCalendar saveSettings(TenantCalendar calendar, AccessContext.User actor) {
        StoredTenantCalendar stored = new StoredTenantCalendar(tenantId, calendar, clock.instant(), actor.userId());
        repository.save(stored);
        return stored;
    }

    private StoredTenantCalendar defaults() {
        return new StoredTenantCalendar(tenantId, TenantCalendar.defaults(), null, null);
    }
}
