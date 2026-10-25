package be.myrmacta.domain.expression.model;

/**
 * Résultat d'une évaluation, normalisé en types JSON : {@code String}, {@code Long}/{@code Double},
 * {@code Boolean}, {@code Map<String,Object>}, {@code List<Object>} ou {@code null}.
 *
 * @param defined {@code false} quand l'expression ne produit aucune valeur (chemin absent, filtre vide)
 */
public record ExpressionResult(Object value, boolean defined) {

    public static ExpressionResult of(Object value) {
        return new ExpressionResult(value, true);
    }

    public static ExpressionResult undefined() {
        return new ExpressionResult(null, false);
    }
}
