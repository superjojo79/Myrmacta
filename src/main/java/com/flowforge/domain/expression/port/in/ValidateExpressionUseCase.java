package com.flowforge.domain.expression.port.in;

import com.flowforge.domain.dossier.model.AccessContext;

public interface ValidateExpressionUseCase {

    /** @throws com.flowforge.domain.expression.model.InvalidExpressionException si l'expression est refusée */
    void validate(AccessContext.User actor, String expression);
}
