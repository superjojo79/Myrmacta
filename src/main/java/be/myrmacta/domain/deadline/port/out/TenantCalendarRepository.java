package be.myrmacta.domain.deadline.port.out;

import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** TODO §120 et §121 — réglage persisté. Vide : le tenant n'a encore rien enregistré. */
public interface TenantCalendarRepository {

    Optional<StoredTenantCalendar> find(String tenantId);

    /**
     * TODO §121 (revue de #33) — exécute {@code work} dans une transaction qui tient le verrou du calendrier du tenant :
     * deux écritures ne s'entrelacent pas. À l'intérieur, relire l'état ({@link #find}) avant de le modifier, puis
     * écrire ; ce qui est lent (téléchargement, lecture d'un fichier) se fait avant, hors verrou. Une exception annule
     * toutes les écritures.
     */
    <T> T locked(String tenantId, Supplier<T> work);

    /**
     * Garantit la ligne de réglage (les défauts si elle manque) et y note qui a modifié le calendrier, quand. Ne touche
     * ni aux saisies manuelles ni aux sources : une opération sur une source n'écrase donc pas ce qu'un autre
     * administrateur a saisi entre-temps.
     */
    void touch(String tenantId, Instant at, String by);

    /** Écrit le fuseau, le week-end et les saisies manuelles. Les sources ne sont pas touchées. */
    void save(StoredTenantCalendar calendar);

    /**
     * Crée ou remplace atomiquement la source et tous ses jours (D-CAL-SOURCE). {@code url} non nul : l'URL entière de
     * la source, conservée chiffrée et qui remplace la précédente ; nul : l'URL déjà conservée ne change pas.
     */
    void saveSource(String tenantId, CalendarSource source, String url);

    /** L'URL entière d'une source {@code ICS_URL}, déchiffrée ; vide si elle n'en a plus (importée par promotion, illisible). */
    Optional<String> findSourceUrl(String tenantId, UUID sourceId);

    /** Supprime la source et ses jours. {@code false} : aucune source de ce tenant avec cet identifiant. */
    boolean deleteSource(String tenantId, UUID sourceId);

    record StoredTenantCalendar(String tenantId, TenantCalendar calendar, Instant updatedAt, String updatedBy) {}
}
