package com.flowforge.infrastructure.config;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.expression.port.in.EvaluateExpressionUseCase;
import com.flowforge.domain.expression.port.in.ValidateExpressionUseCase;
import com.flowforge.domain.expression.port.out.ExpressionEnginePort;
import com.flowforge.domain.expression.usecase.EvaluateExpressionUseCaseImpl;
import com.flowforge.domain.expression.usecase.ValidateExpressionUseCaseImpl;
import com.flowforge.infrastructure.expression.JsonataExpressionEngine;
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
