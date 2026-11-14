package be.myrmacta.infrastructure.web.signature;

import be.myrmacta.domain.signature.port.in.SubmitSignedDocumentUseCase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import org.springframework.web.multipart.MultipartFile;

/**
 * PDF signé reçu en multipart (TODO §138). Le contenu n'est lu en mémoire que lorsque le use case le demande,
 * après le contrôle d'accès et de taille.
 */
public final class MultipartSignedUpload implements SubmitSignedDocumentUseCase.Upload {

    private final MultipartFile file;

    public MultipartSignedUpload(MultipartFile file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    @Override
    public long size() {
        return file.getSize();
    }

    @Override
    public byte[] read() {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
