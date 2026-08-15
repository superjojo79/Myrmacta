package com.flowforge.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.DuplicateDossierRelationException;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;

class DossierExceptionHandlerTest {

    @Test
    void handleConflict_mapsOptimisticLockExceptionTo409() {
        var problem = new DossierExceptionHandler().handleConflict(new OptimisticLockException("stale"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Dossier conflict");
    }

    @Test
    void handleConflict_mapsDuplicateDossierRelationExceptionTo409() {
        var problem = new DossierExceptionHandler()
                .handleConflict(new DuplicateDossierRelationException("already linked"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo("already linked");
    }
}
