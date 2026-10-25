package be.myrmacta.domain.expression.model;

/** Expression refusée avant toute évaluation : syntaxe, construction interdite ou dépassement d'une borne statique. */
public class InvalidExpressionException extends RuntimeException {

    private final Integer position;

    public InvalidExpressionException(String message) {
        this(message, null);
    }

    public InvalidExpressionException(String message, Integer position) {
        super(message);
        this.position = position;
    }

    /** Position (0-based) dans le texte de l'expression quand elle est connue, sinon {@code null}. */
    public Integer position() {
        return position;
    }
}
