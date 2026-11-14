package be.myrmacta.infrastructure.persistence.signature;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataSignedUploadRepository extends JpaRepository<SignedUploadJpaEntity, UUID> {

    List<SignedUploadJpaEntity> findByRequestIdOrderBySequenceAsc(UUID requestId);

    @Query("select u.storageRef from SignedUploadJpaEntity u where u.requestId in"
            + " (select r.id from SignatureRequestJpaEntity r where r.dossierId = :dossierId)")
    List<String> findStorageRefsByDossierId(@Param("dossierId") UUID dossierId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SignedUploadJpaEntity u where u.requestId in"
            + " (select r.id from SignatureRequestJpaEntity r where r.dossierId = :dossierId)")
    int deleteByDossierId(@Param("dossierId") UUID dossierId);
}
