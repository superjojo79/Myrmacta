package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.expression.port.in.EvaluateExpressionUseCase;
import be.myrmacta.domain.expression.port.in.ValidateExpressionUseCase;
import be.myrmacta.domain.expression.port.out.ExpressionEnginePort;
import be.myrmacta.domain.expression.usecase.EvaluateExpressionUseCaseImpl;
import be.myrmacta.domain.expression.usecase.ValidateExpressionUseCaseImpl;
import be.myrmacta.infrastructure.expression.JsonataExpressionEngine;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TODO §75 (langage-expression-metier, LC2) — câblage (domaine sans annotation Spring). */
@Configuration
@EnableConfigurationProperties(ExpressionProperties.class)
class ExpressionConfig {

    @Bean
    ExpressionEnginePort expressionEngine(ExpressionProperties properties) {
        return new JsonataExpressionEngine(properties.toLimits(), Clock.systemUTC());
    }

    @Bean
    ValidateExpressionUseCase validateExpressionUseCase(ExpressionEnginePort engine, TenantAdminGuard guard) {
        return new ValidateExpressionUseCaseImpl(engine, guard);
    }

    @Bean
    EvaluateExpressionUseCase evaluateExpressionUseCase(ExpressionEnginePort engine, TenantAdminGuard guard) {
        return new EvaluateExpressionUseCaseImpl(engine, guard);
    }
}
