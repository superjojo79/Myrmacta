package be.myrmacta.infrastructure.persistence.connector;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "connector_stored_object")
class ConnectorStoredObjectJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "storage_ref", nullable = false, length = 512)
    private String storageRef;

    @Column(name = "dossier_id")
    private UUID dossierId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConnectorStoredObjectJpaEntity() {}

    ConnectorStoredObjectJpaEntity(UUID id, String tenantId, String storageRef, UUID dossierId, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.storageRef = storageRef;
        this.dossierId = dossierId;
        this.createdAt = createdAt;
    }

    UUID dossierId() {
        return dossierId;
    }
}
