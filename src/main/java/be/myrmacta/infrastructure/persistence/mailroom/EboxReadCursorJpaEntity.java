package be.myrmacta.infrastructure.persistence.mailroom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** TODO §150 (D-EBOX-WINDOW) — dernier jour lu entièrement par une instance, pour un registre et une partition (changeset 053). */
@Entity
@Table(name = "ebox_read_cursor")
class EboxReadCursorJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "connector_instance_id", nullable = false)
    private UUID connectorInstanceId;

    @Column(name = "registry_id", nullable = false, length = 50)
    private String registryId;

    @Column(name = "partition_id", nullable = false, length = 100)
    private String partition;

    @Column(name = "read_until", nullable = false)
    private LocalDate readUntil;

    protected EboxReadCursorJpaEntity() {}

    LocalDate readUntil() {
        return readUntil;
    }
}
