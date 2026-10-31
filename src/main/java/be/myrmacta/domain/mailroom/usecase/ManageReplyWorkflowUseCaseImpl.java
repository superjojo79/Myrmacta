package be.myrmacta.domain.mailroom.usecase;

import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting;
import be.myrmacta.domain.mailroom.port.in.ManageReplyWorkflowUseCase;
import be.myrmacta.domain.mailroom.port.out.ReplyWorkflowSettings;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.util.Objects;
import java.util.Optional;

public final class ManageReplyWorkflowUseCaseImpl implements ManageReplyWorkflowUseCase {

    private final String tenantId;
    private final ReplyWorkflowSettings settings;
    private final DossierTypeRepository types;
    private final WorkflowTypeConfigRepository workflowConfigs;

    public ManageReplyWorkflowUseCaseImpl(
            String tenantId, ReplyWorkflowSettings settings, DossierTypeRepository types, WorkflowTypeConfigRepository workflowConfigs) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.types = Objects.requireNonNull(types, "types");
        this.workflowConfigs = Objects.requireNonNull(workflowConfigs, "workflowConfigs");
    }

    @Override
    public Optional<ReplyWorkflowSetting> get(String dossierTypeCode) {
        requireType(dossierTypeCode);
        return settings.find(tenantId, dossierTypeCode);
    }

    @Override
    public ReplyWorkflowSetting set(String dossierTypeCode, ReplyWorkflowSetting.Mode mode, String workflowKey, String childTypeCode) {
        requireType(dossierTypeCode);
        ReplyWorkflowSetting setting = new ReplyWorkflowSetting(tenantId, dossierTypeCode, mode, workflowKey, childTypeCode);
        if (workflowConfigs.findByWorkflowKey(tenantId, setting.workflowKey()).isEmpty()) {
            throw new IllegalArgumentException("No workflow configuration for " + setting.workflowKey());
        }
        if (setting.childTypeCode() != null && types.findByCode(tenantId, setting.childTypeCode()).isEmpty()) {
            throw new IllegalArgumentException("Unknown child dossier type: " + setting.childTypeCode());
        }
        settings.save(setting);
        return setting;
    }

    @Override
    public void clear(String dossierTypeCode) {
        requireType(dossierTypeCode);
        settings.delete(tenantId, dossierTypeCode);
    }

    private void requireType(String dossierTypeCode) {
        if (dossierTypeCode == null || types.findByCode(tenantId, dossierTypeCode).isEmpty()) {
            throw new DossierNotFoundException("Dossier type not found: " + dossierTypeCode);
        }
    }
}
