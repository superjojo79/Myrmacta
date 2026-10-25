package be.myrmacta.domain.parcours.model;

public class ParcoursNotFoundException extends RuntimeException {
    public ParcoursNotFoundException(String key) {
        super("parcours not found: " + key);
    }
}
