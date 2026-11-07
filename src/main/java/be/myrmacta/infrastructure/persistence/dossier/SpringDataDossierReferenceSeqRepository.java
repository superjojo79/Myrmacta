package be.myrmacta.infrastructure.persistence.dossier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataDossierReferenceSeqRepository extends JpaRepository<DossierReferenceSeqJpaEntity, DossierReferenceSeqJpaEntity.Key> {

    /**
     * Incrémente atomiquement le compteur (créé à 1 s'il n'existe pas) et rend la nouvelle valeur. Une seule instruction :
     * le verrou de ligne tient jusqu'à la fin de la transaction, donc pas de doublon ni de trou entre pods.
     */
    @Query(nativeQuery = true, value = """
            insert into dossier_reference_seq (tenant_id, dossier_type_code, "year", last_value)
            values (:tenantId, :code, :year, 1)
            on conflict (tenant_id, dossier_type_code, "year")
            do update set last_value = dossier_reference_seq.last_value + 1
            returning last_value
            """)
    long next(@Param("tenantId") String tenantId, @Param("code") String code, @Param("year") int year);
}
