package be.myrmacta.domain.notification.template.usecase;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Dépôt en mémoire des tests de cas d'usage ; la cascade à quatre niveaux est éprouvée sur PostgreSQL (IT). */
final class InMemoryNotificationTemplateRepository implements NotificationTemplateRepository {

    private final Map<UUID, NotificationTemplate> store = new LinkedHashMap<>();

    @Override
    public NotificationTemplate save(NotificationTemplate template) {
        store.put(template.id(), template);
        return template;
    }

    @Override
    public Optional<NotificationTemplate> findByIdAndTenantId(UUID id, String tenantId) {
        return Optional.ofNullable(store.get(id)).filter(t -> t.tenantId().equals(tenantId));
    }

    @Override
    public PageResult<NotificationTemplate> pageByTenantId(String tenantId, String templateKey, PageQuery query) {
        List<NotificationTemplate> all = new ArrayList<>(store.values().stream()
                .filter(t -> t.tenantId().equals(tenantId))
                .filter(t -> templateKey == null || templateKey.isBlank() || t.templateKey().equals(templateKey))
                .sorted(Comparator.comparing(NotificationTemplate::updatedAt).reversed())
                .toList());
        return PageResult.slice(all, query);
    }

    @Override
    public int nextVersion(String tenantId, String templateKey, String variantKey, String locale) {
        return store.values().stream()
                        .filter(t -> sameCombination(t, tenantId, templateKey, variantKey, locale))
                        .mapToInt(NotificationTemplate::version)
                        .max()
                        .orElse(0)
                + 1;
    }

    @Override
    public Optional<NotificationTemplate> findBestActive(
            String tenantId, String templateKey, String variantKey, String locale) {
        return findActiveExact(tenantId, templateKey, variantKey, locale)
                .or(() -> findActiveExact(tenantId, templateKey, variantKey, null))
                .or(() -> findActiveExact(tenantId, templateKey, null, locale))
                .or(() -> findActiveExact(tenantId, templateKey, null, null));
    }

    @Override
    public Optional<NotificationTemplate> findActiveExact(
            String tenantId, String templateKey, String variantKey, String locale) {
        return store.values().stream()
                .filter(t -> t.status() == TemplateStatus.ACTIVE)
                .filter(t -> sameCombination(t, tenantId, templateKey, variantKey, locale))
                .findFirst();
    }

    NotificationTemplate stored(UUID id) {
        return store.get(id);
    }

    private static boolean sameCombination(
            NotificationTemplate t, String tenantId, String templateKey, String variantKey, String locale) {
        return t.tenantId().equals(tenantId)
                && t.templateKey().equals(templateKey)
                && Objects.equals(t.variantKey(), variantKey)
                && Objects.equals(t.locale(), locale);
    }
}
