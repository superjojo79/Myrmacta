package be.myrmacta.domain.signature;

import be.myrmacta.domain.document.model.DocumentStorageException;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignedUpload;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * TODO §138, D-EID-CHAIN — le document de travail d'une demande, celui qu'on remet au signataire. Pour un
 * fournisseur à PDF apporté, c'est le PDF du dernier téléversement accepté, sinon la source ; pour les
 * autres fournisseurs, toujours la source (aucune lecture des téléversements). Le contrôle d'empreinte est
 * celui du document servi : celle du téléversement, ou celle de la source fixée à l'émission.
 */
public final class SignatureWorkingDocuments {

    /** Référence de stockage et empreinte SHA-256 (hexadécimal minuscule) du document courant. */
    public record Current(String storageRef, String digest) {}

    private final String tenantId;
    private final DocumentStoragePort storage;
    private final SignedUploadRepository uploads;
    private final SignaturePort port;

    public SignatureWorkingDocuments(
            String tenantId, DocumentStoragePort storage, SignedUploadRepository uploads, SignaturePort port) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.uploads = Objects.requireNonNull(uploads, "uploads");
        this.port = Objects.requireNonNull(port, "port");
    }

    /** Document courant de la demande, relu en base si le fournisseur apporte des PDF signés. */
    public Current current(SignatureRequest request) {
        if (!port.capabilities().signedUpload()) {
            return source(request);
        }
        return current(request, uploads.findByRequestId(request.id()));
    }

    /** Document courant, d'après des téléversements déjà lus (leur ordre n'importe pas). */
    public Current current(SignatureRequest request, List<SignedUpload> accepted) {
        return accepted.stream()
                .max(Comparator.comparingInt(SignedUpload::sequence))
                .map(last -> new Current(last.storageRef(), last.digest()))
                .orElseGet(() -> source(request));
    }

    /**
     * Contenu du document courant, empreinte contrôlée.
     *
     * @throws PortalAccessDeniedException document absent, illisible ou dont l'empreinte a changé : le
     *     contenu signé est celui de l'émission ou du téléversement accepté, pas un autre
     */
    public byte[] read(SignatureRequest request, Current current) {
        byte[] content;
        try {
            content = current.storageRef() == null ? null : storage.retrieve(tenantId, current.storageRef());
        } catch (DocumentStorageException ex) {
            throw new PortalAccessDeniedException("working document unreadable for " + request.id());
        }
        if (content == null || current.digest() == null || !digestOf(content).equals(current.digest())) {
            throw new PortalAccessDeniedException("working document digest mismatch for " + request.id());
        }
        return content;
    }

    /** SHA-256 en hexadécimal minuscule. */
    public static String digestOf(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Current source(SignatureRequest request) {
        return new Current(request.sourceStorageRef(), request.sourceDigest());
    }
}
