package be.myrmacta.domain.deadline.usecase;

import be.myrmacta.domain.deadline.model.BelgianFederalHolidays;
import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.CalendarSourceConflictException;
import be.myrmacta.domain.deadline.model.CalendarSourceNotFoundException;
import be.myrmacta.domain.deadline.model.CalendarUrl;
import be.myrmacta.domain.deadline.model.DayRange;
import be.myrmacta.domain.deadline.model.SourcePreview;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.in.ManageTenantCalendarUseCase;
import be.myrmacta.domain.deadline.port.out.IcsCalendarParser;
import be.myrmacta.domain.deadline.port.out.IcsCalendarParser.ParsedIcs;
import be.myrmacta.domain.deadline.port.out.IcsFetcher;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * TODO §120 et §121 — le calendrier lu sans ligne en base est le défaut, non persisté tant qu'on ne l'enregistre pas.
 *
 * <p>Concurrence (revue de #33) : deux administrateurs peuvent agir en même temps. Ce qui est lent (téléchargement,
 * lecture d'un fichier) se fait hors verrou, sur un instantané. Toute écriture passe par
 * {@link TenantCalendarRepository#locked} : l'état est relu sous le verrou, les plafonds, les noms et l'empreinte y
 * sont revérifiés, puis on écrit seulement ce qu'on change. Une opération sur une source n'écrit jamais les saisies
 * manuelles ni le fuseau, que l'autre administrateur a pu modifier entre-temps.
 */
public final class ManageTenantCalendarUseCaseImpl implements ManageTenantCalendarUseCase {

    /** Nom de la source des fériés fédéraux belges (D-CAL-BE-SOURCE). */
    public static final String BELGIAN_SOURCE_NAME = "Fériés fédéraux belges";

    private final String tenantId;
    private final TenantCalendarRepository repository;
    private final TenantAdminGuard guard;
    private final Clock clock;
    private final IcsCalendarParser parser;
    private final IcsFetcher fetcher;

    public ManageTenantCalendarUseCaseImpl(String tenantId, TenantCalendarRepository repository, TenantAdminGuard guard, Clock clock,
                                           IcsCalendarParser parser, IcsFetcher fetcher) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
    }

    @Override
    public StoredTenantCalendar get(AccessContext.User actor) {
        guard.requireTenantAdmin(actor);
        return repository.find(tenantId).orElseGet(this::defaults);
    }

    @Override
    public StoredTenantCalendar replace(AccessContext.User actor, String zoneId, WeekendPattern weekend, List<CalendarEntry> manual) {
        guard.requireTenantAdmin(actor);
        return repository.locked(tenantId, () -> {
            TenantCalendar next = current().withSettings(zoneId, weekend, manual);
            StoredTenantCalendar stored = new StoredTenantCalendar(tenantId, next, clock.instant(), actor.userId());
            repository.save(stored);
            return stored;
        });
    }

    @Override
    public StoredTenantCalendar loadBelgianFederalHolidays(AccessContext.User actor) {
        guard.requireTenantAdmin(actor);
        return repository.locked(tenantId, () -> {
            TenantCalendar current = current();
            Instant now = clock.instant();
            int year = current.today(now).getYear();
            List<CalendarEntry> days = BelgianFederalHolidays.entries(year, year + 1);
            CalendarSource source = current.sources().stream()
                    .filter(existing -> existing.kind() == CalendarSource.Kind.BE_FEDERAL)
                    .findFirst()
                    .map(existing -> existing.reloaded(days, now, actor.userId(), null))
                    .orElseGet(() -> CalendarSource.create(BELGIAN_SOURCE_NAME, CalendarSource.Kind.BE_FEDERAL, days, now,
                            actor.userId(), null));
            TenantCalendar next = current.withSource(source);
            repository.touch(tenantId, now, actor.userId());
            repository.saveSource(tenantId, source, null);
            return new StoredTenantCalendar(tenantId, next, now, actor.userId());
        });
    }

    // ------------------------------------------------------------------ sources par fichier

    @Override
    public SourcePreview previewFileSource(AccessContext.User actor, String name, UUID sourceId, byte[] content) {
        guard.requireTenantAdmin(actor);
        TenantCalendar snapshot = current();
        resolve(snapshot, name, sourceId, FILE);
        return plan(snapshot, actor, name, sourceId, parse(snapshot, content), FILE).preview();
    }

    @Override
    public StoredTenantCalendar applyFileSource(AccessContext.User actor, String name, UUID sourceId, byte[] content,
                                                String expectedSha256) {
        guard.requireTenantAdmin(actor);
        requireSha(expectedSha256);
        TenantCalendar snapshot = current();
        resolve(snapshot, name, sourceId, FILE);
        ParsedIcs parsed = parse(snapshot, content);
        return commit(actor, name, sourceId, parsed, FILE, expectedSha256);
    }

    // ------------------------------------------------------------------ sources par URL

    @Override
    public SourcePreview previewUrlSource(AccessContext.User actor, String name, UUID sourceId, String url) {
        guard.requireTenantAdmin(actor);
        TenantCalendar snapshot = current();
        Download download = download(snapshot, name, sourceId, url);
        return plan(snapshot, actor, name, sourceId, parse(snapshot, download.content()), download.origin()).preview();
    }

    @Override
    public StoredTenantCalendar applyUrlSource(AccessContext.User actor, String name, UUID sourceId, String url, String expectedSha256) {
        guard.requireTenantAdmin(actor);
        requireSha(expectedSha256);
        TenantCalendar snapshot = current();
        Download download;
        ParsedIcs parsed;
        try {
            download = download(snapshot, name, sourceId, url);
            parsed = parse(snapshot, download.content());
        } catch (IllegalArgumentException failure) {
            recordFailure(snapshot, sourceId, failure);
            throw failure;
        }
        return commit(actor, name, sourceId, parsed, download.origin(), expectedSha256);
    }

    @Override
    public void deleteSource(AccessContext.User actor, UUID sourceId) {
        guard.requireTenantAdmin(actor);
        repository.locked(tenantId, () -> {
            if (current().sources().stream().noneMatch(source -> source.id().equals(sourceId))
                    || !repository.deleteSource(tenantId, sourceId)) {
                throw new CalendarSourceNotFoundException(sourceId);
            }
            // D-CAL-CACHE : sans touch, updated_at reste et TenantCalendars.get garderait les jours de la source.
            repository.touch(tenantId, clock.instant(), actor.userId());
            return null;
        });
    }

    // ------------------------------------------------------------------ cœur

    /** Hors verrou : valide l'URL, relit celle qui est conservée au besoin, télécharge. */
    private Download download(TenantCalendar snapshot, String name, UUID sourceId, String url) {
        CalendarUrl given = url == null || url.isBlank() ? null : CalendarUrl.parse(url);
        if (sourceId == null && given == null) {
            throw new IllegalArgumentException("calendar URL is required");
        }
        Origin origin = new Origin(CalendarSource.Kind.ICS_URL, given == null ? null : given.value(), given == null ? null : given.masked());
        resolve(snapshot, name, sourceId, origin);
        CalendarUrl effective = given;
        if (effective == null) {
            effective = repository.findSourceUrl(tenantId, sourceId).map(CalendarUrl::parse)
                    .orElseThrow(() -> new IllegalArgumentException("this source has no readable URL: enter it again"));
        }
        return new Download(fetcher.fetch(effective.value()), origin);
    }

    private record Download(byte[] content, Origin origin) {}

    /** Hors verrou : lit le fichier selon le fuseau et l'année du tenant. */
    private ParsedIcs parse(TenantCalendar snapshot, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("calendar file is required");
        }
        int year = snapshot.today(clock.instant()).getYear();
        return parser.parse(content, ZoneId.of(snapshot.zoneId()), year - 1, year + 2);
    }

    /**
     * Sous verrou : relit l'état, refait le plan (noms, plafonds, source encore là), vérifie l'empreinte, puis écrit la
     * source et note l'auteur. Ni les saisies manuelles ni le fuseau ne sont réécrits.
     */
    private StoredTenantCalendar commit(AccessContext.User actor, String name, UUID sourceId, ParsedIcs parsed, Origin origin,
                                        String expectedSha256) {
        return repository.locked(tenantId, () -> {
            Plan plan = plan(current(), actor, name, sourceId, parsed, origin);
            if (!plan.preview().sha256().equalsIgnoreCase(expectedSha256.strip())) {
                throw new CalendarSourceConflictException("The content changed since the preview: preview it again");
            }
            Instant now = clock.instant();
            repository.touch(tenantId, now, actor.userId());
            repository.saveSource(tenantId, plan.source(), plan.url());
            return new StoredTenantCalendar(tenantId, plan.calendar(), now, actor.userId());
        });
    }

    /**
     * Un rechargement en échec garde ses jours et retient l'erreur, que l'on voit ensuite sur la source. La source est
     * relue sous verrou : un autre administrateur a pu la recharger avec succès pendant ce téléchargement.
     */
    private void recordFailure(TenantCalendar snapshot, UUID sourceId, IllegalArgumentException failure) {
        // Pas de verrou, donc pas de ligne par défaut créée, pour une source qui n'existe pas (ou plus) en tout cas.
        if (sourceId == null || snapshot.sources().stream()
                .noneMatch(source -> source.id().equals(sourceId) && source.kind() == CalendarSource.Kind.ICS_URL)) {
            return;
        }
        repository.locked(tenantId, () -> {
            current().sources().stream()
                    .filter(source -> source.id().equals(sourceId) && source.kind() == CalendarSource.Kind.ICS_URL)
                    .findFirst()
                    .ifPresent(source -> repository.saveSource(tenantId, source.failed(failure.getMessage()), null));
            return null;
        });
    }

    /** La source visée (nulle pour une création) ; refuse une source inconnue, d'un autre type, ou un nom déjà pris. */
    private CalendarSource resolve(TenantCalendar current, String name, UUID sourceId, Origin origin) {
        if (sourceId == null) {
            String wanted = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
            if (current.sources().stream().anyMatch(source -> source.nameKey().equals(wanted))) {
                throw new CalendarSourceConflictException("A source is already named: " + name.strip());
            }
            return null;
        }
        CalendarSource existing = current.sources().stream().filter(source -> source.id().equals(sourceId)).findFirst()
                .orElseThrow(() -> new CalendarSourceNotFoundException(sourceId));
        if (existing.kind() != origin.kind()) {
            throw new IllegalArgumentException("a " + existing.kind() + " source cannot be reloaded from "
                    + (origin.kind() == CalendarSource.Kind.ICS_FILE ? "a file" : "an URL"));
        }
        return existing;
    }

    /** Ce que donnerait le contenu : la source qui en résulte, le calendrier avec elle, et l'aperçu des différences. */
    private Plan plan(TenantCalendar current, AccessContext.User actor, String name, UUID sourceId, ParsedIcs parsed, Origin origin) {
        CalendarSource existing = resolve(current, name, sourceId, origin);
        Instant now = clock.instant();
        CalendarSource source = existing == null
                ? CalendarSource.create(name, origin.kind(), parsed.closedDays(), now, actor.userId(), parsed.sha256())
                : existing.reloaded(parsed.closedDays(), now, actor.userId(), parsed.sha256());
        if (origin.maskedUrl() != null) {
            source = source.withMaskedUrl(origin.maskedUrl());
        }
        TenantCalendar next = current.withSource(source);
        Set<LocalDate> before = existing == null ? Set.of() : days(existing);
        Set<LocalDate> after = days(source);
        Set<LocalDate> added = new HashSet<>(after);
        added.removeAll(before);
        Set<LocalDate> removed = new HashSet<>(before);
        removed.removeAll(after);
        Set<LocalDate> masked = new HashSet<>(after);
        masked.retainAll(next.forcedOpenDays());
        SourcePreview preview = new SourcePreview(source.id(), source.name(), source.kind(), DayRange.compress(added),
                DayRange.compress(removed), DayRange.compress(masked), parsed.ignored(), parsed.sha256(), source.closedDays().size());
        return new Plan(source, next, preview, origin.url());
    }

    private static Set<LocalDate> days(CalendarSource source) {
        Set<LocalDate> days = new HashSet<>();
        source.closedDays().forEach(entry -> days.addAll(entry.days()));
        return days;
    }

    private static void requireSha(String expectedSha256) {
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            throw new IllegalArgumentException("expectedSha256 is required");
        }
    }

    private static final Origin FILE = new Origin(CalendarSource.Kind.ICS_FILE, null, null);

    /**
     * D'où vient le contenu : un fichier, ou une URL (entière à conserver, et forme masquée à montrer ; nuls : inchangées).
     * L'URL est un secret : {@code toString()} ne la dit pas.
     */
    private record Origin(CalendarSource.Kind kind, String url, String maskedUrl) {

        @Override
        public String toString() {
            return "Origin[" + kind + ", " + maskedUrl + "]";
        }
    }

    private record Plan(CalendarSource source, TenantCalendar calendar, SourcePreview preview, String url) {

        @Override
        public String toString() {
            return "Plan[" + source.name() + "]";
        }
    }

    private TenantCalendar current() {
        return repository.find(tenantId).map(StoredTenantCalendar::calendar).orElseGet(TenantCalendar::defaults);
    }

    private StoredTenantCalendar defaults() {
        return new StoredTenantCalendar(tenantId, TenantCalendar.defaults(), null, null);
    }
}
