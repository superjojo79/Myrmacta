package be.myrmacta.infrastructure.persistence.deadline;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * TODO §121 (D-CAL-SOURCE, D-CAL-ICS-URL) — source du calendrier (changesets 047 et 048). {@code urlCiphertext} est l'URL
 * entière d'une source ICS_URL, chiffrée : elle ne sort de l'adaptateur que pour être téléchargée. La base ne garde en
 * clair que sa forme masquée.
 */
@Entity
@Table(name = "tenant_calendar_source")
class TenantCalendarSourceJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "url_ciphertext")
    private String urlCiphertext;

    @Column(name = "loaded_at")
    private Instant loadedAt;

    @Column(name = "loaded_by")
    private String loadedBy;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "masked_url", length = 300)
    private String maskedUrl;

    protected TenantCalendarSourceJpaEntity() {}

    static TenantCalendarSourceJpaEntity fromDomain(String tenantId, CalendarSource source) {
        TenantCalendarSourceJpaEntity entity = new TenantCalendarSourceJpaEntity();
        entity.id = source.id();
        entity.tenantId = tenantId;
        entity.applyDomain(source);
        return entity;
    }

    void applyDomain(CalendarSource source) {
        this.name = source.name();
        this.kind = source.kind().name();
        this.loadedAt = source.loadedAt();
        this.loadedBy = source.loadedBy();
        this.contentSha256 = source.contentSha256();
        this.lastError = source.lastError();
        this.maskedUrl = source.maskedUrl();
    }

    CalendarSource toDomain(List<CalendarEntry> closedDays) {
        return new CalendarSource(id, name, CalendarSource.Kind.parse(kind), closedDays, loadedAt, loadedBy, contentSha256,
                lastError, maskedUrl);
    }

    UUID id() {
        return id;
    }

    String tenantId() {
        return tenantId;
    }

    String urlCiphertext() {
        return urlCiphertext;
    }

    void urlCiphertext(String ciphertext) {
        this.urlCiphertext = ciphertext;
    }

    /** TODO §124 (D-CAL-URL-PURGE) — plus de secret ni de forme masquée : l'URL est à ressaisir. */
    void clearStoredUrl() {
        this.urlCiphertext = null;
        this.maskedUrl = null;
    }
}
