package com.flowforge.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.DuplicateDossierRelationException;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @Test
    void handleInvalidElement_mapsTo400() {
        var problem = new DossierExceptionHandler()
                .handleInvalidElement(new InvalidDossierElementException("metadata.url must be a valid absolute URI for LINK"));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Invalid dossier element");
    }

    @Test
    void handleUploadTooLarge_mapsTo413() {
        // Not exercisable end-to-end via MockMvc: MockMvcRequestBuilders.multipart(...).file(...)
        // builds an already-parsed MockMultipartHttpServletRequest, bypassing the container-level
        // (Tomcat) multipart size enforcement that throws this exception in production — same
        // category of gap as the project's other "smoke réel hors CI" items. Verified here as a
        // direct unit test of the mapping instead.
        var problem = new DossierExceptionHandler()
                .handleUploadTooLarge(new MaxUploadSizeExceededException(26_214_400L));

        assertThat(problem.getStatus()).isEqualTo(413);
        assertThat(problem.getTitle()).isEqualTo("Upload too large");
    }
}
