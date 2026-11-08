package be.myrmacta.domain.notification.template.port.out;

import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository {

    NotificationTemplate save(NotificationTemplate template);

    Optional<NotificationTemplate> findByIdAndTenantId(UUID id, String tenantId);

    /**
     * @param templateKey filtre facultatif ; nul ou vide : tous les gabarits du tenant
     */
    PageResult<NotificationTemplate> pageByTenantId(String tenantId, String templateKey, PageQuery query);

    /** Numéro de la prochaine version de la combinaison (max + 1, ou 1). */
    int nextVersion(String tenantId, String templateKey, String variantKey, String locale);

    /** Version ACTIVE la mieux classée, par quatre niveaux de repli. */
    Optional<NotificationTemplate> findBestActive(
            String tenantId, String templateKey, String variantKey, String locale);

    /** Version ACTIVE exacte d'une combinaison (archivée quand une autre version s'active). */
    Optional<NotificationTemplate> findActiveExact(
            String tenantId, String templateKey, String variantKey, String locale);
}
