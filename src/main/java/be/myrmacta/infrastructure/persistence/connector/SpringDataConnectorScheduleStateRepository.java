package be.myrmacta.infrastructure.persistence.connector;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataConnectorScheduleStateRepository
        extends JpaRepository<ConnectorScheduleStateJpaEntity, UUID> {

    /** Insertion stricte : une clé primaire déjà prise (autre exécutant) lève une violation — pas de fusion silencieuse. */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(
            "insert into ConnectorScheduleStateJpaEntity (connectorInstanceId, lastTriggeredAt) values (:id, :at)")
    int insertFirst(
            @org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("at") Instant at);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(
            "update ConnectorScheduleStateJpaEntity e set e.lastTriggeredAt = :next "
                    + "where e.connectorInstanceId = :id and e.lastTriggeredAt = :expected")
    int compareAndSet(
            @org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("expected") Instant expected,
            @org.springframework.data.repository.query.Param("next") Instant next);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(
            "delete from ConnectorScheduleStateJpaEntity e where e.connectorInstanceId = :id and e.lastTriggeredAt = :at")
    int deleteIfAt(
            @org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("at") Instant at);
}
