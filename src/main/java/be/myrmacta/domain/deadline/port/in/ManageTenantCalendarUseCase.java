package be.myrmacta.domain.deadline.port.in;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.SourcePreview;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import be.myrmacta.domain.dossier.model.AccessContext;
import java.util.List;
import java.util.UUID;

/**
 * TODO §120 et §121 — lecture et remplacement du calendrier du tenant, et source des fériés fédéraux belges.
 * Sources ICS par fichier (D-CAL-SOURCE, D-CAL-ICS, D-CAL-ICS-PREVIEW) : un aperçu sans écriture, puis l'application.
 */
public interface ManageTenantCalendarUseCase {

    StoredTenantCalendar get(AccessContext.User actor);

    /** Remplace le fuseau, le week-end et les saisies manuelles ; les sources ne bougent pas (D-CAL-API). */
    StoredTenantCalendar replace(AccessContext.User actor, String zoneId, WeekendPattern weekend, List<CalendarEntry> manual);

    /**
     * Crée ou recharge la source {@code BE_FEDERAL} (D-CAL-BE-SOURCE) : les fériés de l'année civile courante (fuseau
     * du tenant) et de la suivante. Idempotent ; ne touche pas aux saisies manuelles.
     */
    StoredTenantCalendar loadBelgianFederalHolidays(AccessContext.User actor);

    /**
     * Aperçu d'un fichier ICS. {@code sourceId} nul : création d'une source de ce nom ; sinon rechargement de cette
     * source (son nom est conservé, {@code name} est ignoré). N'écrit rien.
     *
     * @throws be.myrmacta.domain.deadline.model.CalendarSourceNotFoundException source inconnue
     * @throws be.myrmacta.domain.deadline.model.CalendarSourceConflictException nom déjà pris
     * @throws IllegalArgumentException fichier invalide, ou source qui ne se recharge pas depuis un fichier
     */
    SourcePreview previewFileSource(AccessContext.User actor, String name, UUID sourceId, byte[] content);

    /**
     * Crée ou recharge la source avec le contenu. {@code expectedSha256} est celui de l'aperçu : s'il ne correspond
     * plus au contenu, rien n'est écrit.
     *
     * @throws be.myrmacta.domain.deadline.model.CalendarSourceConflictException contenu différent de l'aperçu, ou nom pris
     */
    StoredTenantCalendar applyFileSource(AccessContext.User actor, String name, UUID sourceId, byte[] content, String expectedSha256);

    /** Supprime la source et ses jours. @throws be.myrmacta.domain.deadline.model.CalendarSourceNotFoundException source inconnue */
    void deleteSource(AccessContext.User actor, UUID sourceId);
}
