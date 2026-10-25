package be.myrmacta.domain.parcours.model;

/** Parcours incohérent : jamais compilé, jamais écrit. Le message nomme l'étape fautive. */
public class InvalidParcoursException extends RuntimeException {
    public InvalidParcoursException(String message) {
        super(message);
    }
}
