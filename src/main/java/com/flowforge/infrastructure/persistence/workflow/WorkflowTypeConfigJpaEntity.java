package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "workflow_type_config",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_workflow_type_config_tenant_key",
                columnNames = {"tenant_id", "workflow_key"}))
class WorkflowTypeConfigJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "workflow_key", nullable = false, length = 128)
    private String workflowKey;

    @Column(nullable = false)
    private String label;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "status_definitions", nullable = false, columnDefinition = "json")
    private List<WorkflowStatusDefinition> statusDefinitions = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_dossier_statuses", nullable = false, columnDefinition = "json")
    private List<DossierStatus> allowedDossierStatuses = List.of();

    @Enumerated(EnumType.STRING)
    @Column(name = "on_start_dossier_status", length = 32)
    private DossierStatus onStartDossierStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "observer_user_ids", nullable = false, columnDefinition = "json")
    private List<String> observerUserIds = List.of();

    protected WorkflowTypeConfigJpaEntity() {
        // JPA
    }

    private WorkflowTypeConfigJpaEntity(WorkflowTypeConfig config) {
        this.id = config.id();
        this.tenantId = config.tenantId();
        this.workflowKey = config.workflowKey();
        this.label = config.label();
        this.statusDefinitions = config.statusDefinitions();
        this.allowedDossierStatuses = config.allowedDossierStatuses();
        this.onStartDossierStatus = config.onStartDossierStatus();
        this.observerUserIds = config.observerUserIds();
    }

    static WorkflowTypeConfigJpaEntity fromDomain(WorkflowTypeConfig config) {
        return new WorkflowTypeConfigJpaEntity(config);
    }

    WorkflowTypeConfig toDomain() {
        return new WorkflowTypeConfig(
                id,
                tenantId,
                workflowKey,
                label,
                statusDefinitions,
                allowedDossierStatuses,
                onStartDossierStatus,
                observerUserIds == null ? List.of() : observerUserIds);
    }
}
