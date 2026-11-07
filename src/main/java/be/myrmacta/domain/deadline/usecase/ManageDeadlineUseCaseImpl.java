package be.myrmacta.domain.deadline.usecase;

import be.myrmacta.domain.deadline.model.DayCount;
import be.myrmacta.domain.deadline.model.DeadlineSetting;
import be.myrmacta.domain.deadline.port.in.ManageDeadlineUseCase;
import be.myrmacta.domain.deadline.port.out.DeadlineSettings;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.user.model.UserGroupNotFoundException;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import java.util.Objects;
import java.util.Optional;

/** TODO §115 (D-DEADLINE-FIELD) — réglage d'échéance d'un type : champ {@code DATE} du type, groupe d'escalade existant. */
public final class ManageDeadlineUseCaseImpl implements ManageDeadlineUseCase {

    private final String tenantId;
    private final DeadlineSettings settings;
    private final DossierTypeRepository types;
    private final UserGroupsUseCase groups;

    public ManageDeadlineUseCaseImpl(String tenantId, DeadlineSettings settings, DossierTypeRepository types, UserGroupsUseCase groups) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.types = Objects.requireNonNull(types, "types");
        this.groups = Objects.requireNonNull(groups, "groups");
    }

    @Override
    public Optional<DeadlineSetting> get(String dossierTypeCode) {
        requireType(dossierTypeCode);
        return settings.find(tenantId, dossierTypeCode);
    }

    @Override
    public DeadlineSetting set(String dossierTypeCode, String fieldName, int reminderDays, String escalationGroup) {
        return set(dossierTypeCode, fieldName, reminderDays, escalationGroup, DayCount.CALENDAR);
    }

    @Override
    public DeadlineSetting set(
            String dossierTypeCode, String fieldName, int reminderDays, String escalationGroup, DayCount reminderDayCount) {
        DossierType type = requireType(dossierTypeCode);
        DeadlineSetting setting = new DeadlineSetting(tenantId, dossierTypeCode, fieldName, reminderDays, escalationGroup, reminderDayCount);
        boolean dateField = type.fieldDefinitions().stream()
                .anyMatch(definition -> definition.name().equals(setting.fieldName()) && definition.type() == FieldType.DATE);
        if (!dateField) {
            throw new IllegalArgumentException("Not a DATE field of " + dossierTypeCode + ": " + setting.fieldName());
        }
        if (setting.escalationGroup() != null) {
            try {
                groups.get(setting.escalationGroup());
            } catch (UserGroupNotFoundException unknown) {
                throw new IllegalArgumentException("Unknown user group: " + setting.escalationGroup());
            }
        }
        settings.save(setting);
        return setting;
    }

    @Override
    public void clear(String dossierTypeCode) {
        requireType(dossierTypeCode);
        settings.delete(tenantId, dossierTypeCode);
    }

    private DossierType requireType(String dossierTypeCode) {
        if (dossierTypeCode == null) {
            throw new DossierNotFoundException("Dossier type not found: null");
        }
        return types.findByCode(tenantId, dossierTypeCode)
                .orElseThrow(() -> new DossierNotFoundException("Dossier type not found: " + dossierTypeCode));
    }
}
