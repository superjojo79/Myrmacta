package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.expression.model.ExpressionResult;
import com.flowforge.domain.expression.model.InvalidExpressionException;
import com.flowforge.domain.expression.port.in.EvaluateExpressionUseCase;
import com.flowforge.domain.expression.port.in.ValidateExpressionUseCase;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §75 (LC2) — validation et évaluation <b>à blanc</b> d'une expression métier sur un contexte d'exemple fourni par
 * l'appelant : aucun effet de bord, aucune donnée du tenant lue. Mêmes bornes qu'à l'exécution. Éditeurs BPMN :
 * {@code TENANT_ADMIN}, {@code WORKFLOW_ADMIN}.
 */
@RestController
@RequestMapping("/api/expressions")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
class ExpressionController {

    private final ValidateExpressionUseCase validateUseCase;
    private final EvaluateExpressionUseCase evaluateUseCase;
    private final AccessContextFactory accessContextFactory;

    ExpressionController(
            ValidateExpressionUseCase validateUseCase,
            EvaluateExpressionUseCase evaluateUseCase,
            AccessContextFactory accessContextFactory) {
        this.validateUseCase = validateUseCase;
        this.evaluateUseCase = evaluateUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    /** Réponse 200 même pour une expression invalide : un éditeur affiche l'erreur, il ne la traite pas comme une panne. */
    @PostMapping(path = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    ValidateExpressionResponse validateExpression(@RequestBody ExpressionRequest body, Authentication authentication) {
        try {
            validateUseCase.validate(accessContextFactory.user(authentication), body == null ? null : body.expression());
            return new ValidateExpressionResponse(true, null, null);
        } catch (InvalidExpressionException e) {
            return new ValidateExpressionResponse(false, e.getMessage(), e.position());
        }
    }

    @PostMapping(path = "/evaluate", consumes = MediaType.APPLICATION_JSON_VALUE)
    EvaluateExpressionResponse evaluateExpression(@RequestBody ExpressionRequest body, Authentication authentication) {
        ExpressionResult result = evaluateUseCase.evaluate(
                accessContextFactory.user(authentication),
                body == null ? null : body.expression(),
                body == null ? null : body.context());
        return new EvaluateExpressionResponse(result.defined(), result.value());
    }

    record ExpressionRequest(String expression, Map<String, Object> context) {}

    record ValidateExpressionResponse(boolean valid, String message, Integer position) {}

    record EvaluateExpressionResponse(boolean defined, Object value) {}
}
