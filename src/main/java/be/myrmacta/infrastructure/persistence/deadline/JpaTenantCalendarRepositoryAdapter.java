package be.myrmacta.infrastructure.persistence.deadline;

import be.myrmacta.domain.connector.port.out.ConnectorSecretCipher;
import be.myrmacta.domain.deadline.model.BusinessCalendar;
import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository;
import be.myrmacta.domain.deadline.port.out.TenantCalendars;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TODO §120, §121 et §122 — persistance du calendrier du tenant en JPA : {@link TenantCalendarJpaEntity} (réglage, 046),
 * {@link TenantCalendarSourceJpaEntity} et {@link TenantCalendarEntryJpaEntity} (047 et 048). Une saisie manuelle a
 * {@code source_id} nul ; supprimer une source supprime ses jours (cascade de la clé étrangère).
 *
 * <p>L'URL d'une source ICS_URL est un secret (D-CAL-ICS-URL) : elle est chiffrée ici, par {@link ConnectorSecretCipher},
 * et ne sort de cette classe que pour être téléchargée ; la base ne garde en clair que sa forme masquée.
 */
@Component
class JpaTenantCalendarRepositoryAdapter implements TenantCalendarRepository, TenantCalendars {

    private static final Logger LOG = LoggerFactory.getLogger(JpaTenantCalendarRepositoryAdapter.class);

    /** Insertions groupées par lot de cette taille : une source peut compter 10 000 périodes. */
    private static final int INSERT_BATCH_SIZE = 500;

    private final SpringDataTenantCalendarRepository headers;
    private final SpringDataTenantCalendarSourceRepository sources;
    private final SpringDataTenantCalendarEntryRepository entries;
    private final ConnectorSecretCipher cipher;
    private final TransactionTemplate transaction;
    private final EntityManager entityManager;

    JpaTenantCalendarRepositoryAdapter(
            SpringDataTenantCalendarRepository headers,
            SpringDataTenantCalendarSourceRepository sources,
            SpringDataTenantCalendarEntryRepository entries,
            ConnectorSecretCipher cipher,
            PlatformTransactionManager transactionManager,
            EntityManager entityManager) {
        this.headers = headers;
        this.sources = sources;
        this.entries = entries;
        this.cipher = cipher;
        this.transaction = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    /**
     * Transaction qui tient le verrou de la ligne {@code tenant_calendar} du tenant jusqu'à sa fin : les écritures des
     * administrateurs se suivent, chacune relisant l'état de la précédente. La ligne est créée avec les défauts si elle
     * manque (elle serait créée par la première écriture de toute façon).
     */
    @Override
    public <T> T locked(String tenantId, Supplier<T> work) {
        return transaction.execute(status -> {
            headers.insertDefaultsIfAbsent(tenantId);
            headers.lockByTenantId(tenantId)
                    .orElseThrow(() -> new IllegalStateException("tenant calendar row cannot be locked"));
            return work.get();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessCalendar get(String tenantId) {
        return find(tenantId).map(stored -> stored.calendar().toCalendar()).orElseGet(BusinessCalendar::defaults);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredTenantCalendar> find(String tenantId) {
        Optional<TenantCalendarJpaEntity> header = headers.findById(tenantId);
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<CalendarEntry> manual = new ArrayList<>();
        Map<UUID, List<CalendarEntry>> bySource = new LinkedHashMap<>();
        for (TenantCalendarEntryJpaEntity entry : entries.findByTenantId(tenantId)) {
            if (entry.sourceId() == null) {
                manual.add(entry.toDomain());
            } else {
                bySource.computeIfAbsent(entry.sourceId(), id -> new ArrayList<>()).add(entry.toDomain());
            }
        }
        List<CalendarSource> loaded = sources.findAllOfTenant(tenantId).stream()
                .map(source -> source.toDomain(bySource.getOrDefault(source.id(), List.of())))
                .toList();
        TenantCalendarJpaEntity row = header.get();
        return Optional.of(new StoredTenantCalendar(tenantId, new TenantCalendar(row.zoneId(), row.weekend(), manual, loaded),
                row.updatedAt(), row.updatedBy()));
    }

    @Override
    @Transactional
    public void save(StoredTenantCalendar calendar) {
        String tenantId = calendar.tenantId();
        TenantCalendar value = calendar.calendar();
        TenantCalendarJpaEntity header = headers.findById(tenantId)
                .orElseGet(() -> TenantCalendarJpaEntity.defaultsFor(tenantId, calendar.updatedAt(), calendar.updatedBy()));
        header.applySettings(value.zoneId(), value.weekend());
        header.touch(calendar.updatedAt(), calendar.updatedBy());
        headers.save(header);
        entries.deleteManualOfTenant(tenantId);
        insertEntries(tenantId, null, value.manual());
    }

    @Override
    @Transactional
    public void touch(String tenantId, Instant at, String by) {
        TenantCalendarJpaEntity header = headers.findById(tenantId)
                .orElseGet(() -> TenantCalendarJpaEntity.defaultsFor(tenantId, at, by));
        header.touch(at, by);
        headers.save(header);
    }

    @Override
    @Transactional
    public void saveSource(String tenantId, CalendarSource source, String url) {
        // L'identifiant d'une source existe pour un seul tenant : une collision n'écrit rien, surtout pas des périodes
        // rattachées à la source d'autrui.
        Optional<TenantCalendarSourceJpaEntity> existing = sources.findById(source.id());
        if (existing.isPresent() && !tenantId.equals(existing.get().tenantId())) {
            throw new IllegalStateException("calendar source id is already used by another tenant");
        }
        TenantCalendarSourceJpaEntity entity = existing.orElseGet(() -> TenantCalendarSourceJpaEntity.fromDomain(tenantId, source));
        entity.applyDomain(source);
        if (url != null) {
            entity.urlCiphertext(cipher.encrypt(tenantId, url));
        } else if (source.kind() != CalendarSource.Kind.ICS_URL || source.maskedUrl() == null) {
            // Une source qui n'est (plus) une source par URL, ou dont l'URL est à ressaisir, ne garde aucun secret.
            entity.urlCiphertext(null);
        }
        sources.save(entity);
        // La suppression groupée écrit d'abord la source (flush) puis vide le contexte ; les périodes viennent ensuite.
        entries.deleteOfSource(tenantId, source.id());
        insertEntries(tenantId, source.id(), source.closedDays());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findSourceUrl(String tenantId, UUID sourceId) {
        Optional<String> stored = sources.findByIdAndTenantId(sourceId, tenantId).map(TenantCalendarSourceJpaEntity::urlCiphertext);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(cipher.decrypt(tenantId, stored.get()));
        } catch (RuntimeException unreadable) {
            // Clé changée ou valeur altérée : on demande l'URL de nouveau. Ni l'URL ni la cause n'est journalisée.
            LOG.warn("The stored URL of calendar source {} cannot be decrypted", sourceId);
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public boolean deleteSource(String tenantId, UUID sourceId) {
        return sources.deleteOfTenant(tenantId, sourceId) > 0;
    }

    /** Insère par lots, puis vide le contexte : 10 000 périodes ne doivent pas rester en mémoire. */
    private void insertEntries(String tenantId, UUID sourceId, List<CalendarEntry> values) {
        if (values.isEmpty()) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        Integer previous = session.getJdbcBatchSize();
        session.setJdbcBatchSize(INSERT_BATCH_SIZE);
        try {
            for (CalendarEntry value : values) {
                entityManager.persist(TenantCalendarEntryJpaEntity.fromDomain(tenantId, sourceId, value));
            }
            entityManager.flush();
            entityManager.clear();
        } finally {
            session.setJdbcBatchSize(previous);
        }
    }
}
