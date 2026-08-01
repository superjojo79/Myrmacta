package com.flowforge.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;

class DossierExceptionHandlerTest {

    @Test
    void handleConflict_mapsOptimisticLockExceptionTo409() {
        var problem = new DossierExceptionHandler().handleConflict(new OptimisticLockException("stale"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Dossier conflict");
    }
}
