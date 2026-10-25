package be.myrmacta.domain.expression.model;

/** Échec à l'évaluation : erreur d'exécution de l'expression ou dépassement d'une borne dynamique (délai, taille…). */
public class ExpressionEvaluationException extends RuntimeException {

    public ExpressionEvaluationException(String message) {
        super(message);
    }

    public ExpressionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
