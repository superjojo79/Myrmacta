package be.myrmacta.infrastructure.persistence.dossier;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataDossierUniqueValueRepository extends JpaRepository<DossierUniqueValueJpaEntity, DossierUniqueValueJpaEntity.Key> {

    /** Détenteur d'une valeur : référence et identifiant du dossier. */
    interface Holder {
        String getReference();

        UUID getDossierId();
    }

    /** Verrou consultatif partagé de la transaction ; le comptage évite de lire une colonne {@code void}. */
    @Query(nativeQuery = true, value = """
            select count(*) from (select pg_advisory_xact_lock_shared(:namespace,
                hashtext(current_schema() || ':' || cast(:tenantId as text) || ':' || cast(:code as text)))) l
            """)
    long lockShared(@Param("namespace") int namespace, @Param("tenantId") String tenantId, @Param("code") String code);

    /** Verrou consultatif exclusif de la transaction. */
    @Query(nativeQuery = true, value = """
            select count(*) from (select pg_advisory_xact_lock(:namespace,
                hashtext(current_schema() || ':' || cast(:tenantId as text) || ':' || cast(:code as text)))) l
            """)
    long lockExclusive(@Param("namespace") int namespace, @Param("tenantId") String tenantId, @Param("code") String code);

    @Modifying(flushAutomatically = true)
    @Query("delete from DossierUniqueValueJpaEntity u where u.dossierId = :dossierId")
    int deleteForDossier(@Param("dossierId") UUID dossierId);

    @Modifying(flushAutomatically = true)
    @Query("delete from DossierUniqueValueJpaEntity u where u.tenantId = :tenantId and u.dossierTypeCode = :code")
    int deleteForType(@Param("tenantId") String tenantId, @Param("code") String code);

    /** {@code on conflict do nothing} : une violation avorterait la transaction (le détenteur ne serait plus lisible). */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into dossier_unique_value (tenant_id, dossier_type_code, field_name, field_value, dossier_id)
            values (:tenantId, :code, :field, :value, :dossierId)
            on conflict do nothing
            """)
    int insertIfFree(@Param("tenantId") String tenantId, @Param("code") String code, @Param("field") String field,
            @Param("value") String value, @Param("dossierId") UUID dossierId);

    @Query("""
            select d.reference as reference, u.dossierId as dossierId
            from DossierUniqueValueJpaEntity u join DossierJpaEntity d on d.id = u.dossierId
            where u.tenantId = :tenantId and u.dossierTypeCode = :code and u.fieldName = :field and u.fieldValue = :value
            """)
    List<Holder> holders(@Param("tenantId") String tenantId, @Param("code") String code, @Param("field") String field,
            @Param("value") String value);
}
