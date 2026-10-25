package be.myrmacta.domain.expression.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;

public interface ValidateExpressionUseCase {

    /** @throws be.myrmacta.domain.expression.model.InvalidExpressionException si l'expression est refusée */
    void validate(AccessContext.User actor, String expression);
}
