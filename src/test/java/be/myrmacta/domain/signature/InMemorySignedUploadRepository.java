package be.myrmacta.domain.signature;

import be.myrmacta.domain.signature.model.SignedUpload;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Dépôt en mémoire des téléversements acceptés (TODO §138), avec les deux unicités de la table. */
public class InMemorySignedUploadRepository implements SignedUploadRepository {

    public final List<SignedUpload> uploads = new ArrayList<>();
    private final InMemorySignatureRepository requests;

    public InMemorySignedUploadRepository(InMemorySignatureRepository requests) {
        this.requests = requests;
    }

    @Override
    public synchronized List<SignedUpload> findByRequestId(UUID requestId) {
        return uploads.stream()
                .filter(u -> u.requestId().equals(requestId))
                .sorted(Comparator.comparingInt(SignedUpload::sequence))
                .toList();
    }

    @Override
    public synchronized void save(SignedUpload upload) {
        boolean clash = uploads.stream().anyMatch(u -> u.requestId().equals(upload.requestId())
                && (u.sequence() == upload.sequence()
                        || u.evidence().certificateFingerprint().equals(upload.evidence().certificateFingerprint())));
        if (clash) {
            throw new IllegalStateException("unique constraint violated");
        }
        uploads.add(upload);
    }

    @Override
    public synchronized List<String> findStorageRefsByDossierId(UUID dossierId) {
        return uploads.stream().filter(u -> inDossier(u, dossierId)).map(SignedUpload::storageRef).toList();
    }

    @Override
    public synchronized int deleteAllByDossierId(UUID dossierId) {
        List<SignedUpload> gone = uploads.stream().filter(u -> inDossier(u, dossierId)).toList();
        uploads.removeAll(gone);
        return gone.size();
    }

    private boolean inDossier(SignedUpload upload, UUID dossierId) {
        return requests.findById(upload.requestId()).map(r -> r.dossierId().equals(dossierId)).orElse(false);
    }
}
