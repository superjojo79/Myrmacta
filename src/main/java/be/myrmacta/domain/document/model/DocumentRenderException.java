package be.myrmacta.domain.document.model;

public class DocumentRenderException extends RuntimeException {

    public DocumentRenderException(String message) {
        super(message);
    }

    public DocumentRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
