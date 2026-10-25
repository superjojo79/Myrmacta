package be.myrmacta.domain.parcours.model;

/** Le parcours a changé depuis sa lecture (version attendue périmée) : rien n'est écrasé en silence. */
public class ParcoursConflictException extends RuntimeException {
    public ParcoursConflictException(String key) {
        super("the parcours '" + key + "' was changed by someone else: reload it before saving");
    }
}
