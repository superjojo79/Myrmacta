package be.myrmacta.domain.signature.port.out;

/** Une demande {@code PENDING} existe déjà pour ce couple (process, activité) : rejeu du delegate. */
public class SignatureConflictException extends RuntimeException {
    public SignatureConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
