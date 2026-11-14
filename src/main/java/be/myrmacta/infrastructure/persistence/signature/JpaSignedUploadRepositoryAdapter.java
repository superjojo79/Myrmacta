package be.myrmacta.infrastructure.persistence.signature;

import be.myrmacta.domain.signature.model.SignedUpload;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** TODO §138 — persistance JPA des téléversements acceptés (PDF signés hors de Myrmacta). */
@Component
class JpaSignedUploadRepositoryAdapter implements SignedUploadRepository {

    private final SpringDataSignedUploadRepository uploads;

    JpaSignedUploadRepositoryAdapter(SpringDataSignedUploadRepository uploads) {
        this.uploads = uploads;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SignedUpload> findByRequestId(UUID requestId) {
        return uploads.findByRequestIdOrderBySequenceAsc(requestId).stream()
                .map(SignedUploadJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void save(SignedUpload upload) {
        Objects.requireNonNull(upload, "upload");
        // Flush immédiat : une contrainte d'unicité violée remonte ici, pas au commit de l'appelant.
        uploads.saveAndFlush(SignedUploadJpaEntity.fromDomain(upload));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findStorageRefsByDossierId(UUID dossierId) {
        return uploads.findStorageRefsByDossierId(Objects.requireNonNull(dossierId, "dossierId"));
    }

    @Override
    @Transactional
    public int deleteAllByDossierId(UUID dossierId) {
        return uploads.deleteByDossierId(Objects.requireNonNull(dossierId, "dossierId"));
    }
}
