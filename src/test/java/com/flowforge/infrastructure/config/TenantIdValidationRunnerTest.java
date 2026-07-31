package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.infrastructure.tenant.InvalidTenantIdException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class TenantIdValidationRunnerTest {

    @Test
    void run_withValidId_doesNotThrow() {
        var runner = new TenantIdValidationRunner(new TenantProperties("tenant_dev"));

        assertThatCode(() -> runner.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void run_withInvalidId_throwsInvalidTenantIdException() {
        var runner = new TenantIdValidationRunner(new TenantProperties("tenant-a"));

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(InvalidTenantIdException.class);
    }
}
