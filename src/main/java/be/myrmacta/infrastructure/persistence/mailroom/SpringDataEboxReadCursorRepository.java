package be.myrmacta.infrastructure.persistence.mailroom;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataEboxReadCursorRepository extends JpaRepository<EboxReadCursorJpaEntity, UUID> {

    @Query("""
            select c from EboxReadCursorJpaEntity c
            where c.connectorInstanceId = :instanceId and c.registryId = :registryId and c.partition = :partition
            """)
    Optional<EboxReadCursorJpaEntity> find(
            @Param("instanceId") UUID instanceId, @Param("registryId") String registryId, @Param("partition") String partition);

    /** Le curseur ne recule jamais : deux passages concurrents gardent le jour le plus récent. */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into ebox_read_cursor (id, connector_instance_id, registry_id, partition_id, read_until)
            values (cast(:id as uuid), cast(:instanceId as uuid), :registryId, :partition, cast(:readUntil as date))
            on conflict (connector_instance_id, registry_id, partition_id)
            do update set read_until = greatest(ebox_read_cursor.read_until, excluded.read_until)
            """)
    int upsert(
            @Param("id") UUID id,
            @Param("instanceId") UUID instanceId,
            @Param("registryId") String registryId,
            @Param("partition") String partition,
            @Param("readUntil") LocalDate readUntil);
}
