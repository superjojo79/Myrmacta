package be.myrmacta.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.dossier.model.DossierTypeSchemaIncompatibleException;
import be.myrmacta.domain.dossier.model.DuplicateDossierRelationException;
import be.myrmacta.domain.dossier.model.InvalidDossierElementException;
import jakarta.persistence.OptimisticLockException;
import java.util.List;
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
        var problem = new DossierExceptionHandler()
                .handleUploadTooLarge(new MaxUploadSizeExceededException(26_214_400L));

        assertThat(problem.getStatus()).isEqualTo(413);
        assertThat(problem.getTitle()).isEqualTo("Upload too large");
    }

    @Test
    void handleConflict_mapsDossierNotEligibleForPurgeTo409() {
        var problem = new DossierExceptionHandler()
                .handleConflict(new be.myrmacta.domain.dossier.model.DossierNotEligibleForPurgeException(
                        "Purge enqueue requires CLOTURE or ARCHIVE, was OUVERT"));

        assertThat(problem.getStatus()).isEqualTo(409);
    }

    @Test
    void handleSchemaIncompatible_mapsTo409WithCountAndSamples() {
        var problem = new DossierExceptionHandler().handleSchemaIncompatible(
                new DossierTypeSchemaIncompatibleException(3, List.of("DOS-1", "DOS-2")));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Dossier type schema incompatible");
        assertThat(problem.getDetail()).contains("3").contains("DOS-1").contains("DOS-2");
        assertThat(problem.getProperties()).containsEntry("incompatibleCount", 3);
        assertThat(problem.getProperties()).containsEntry("sampleReferences", List.of("DOS-1", "DOS-2"));
    }

    @Test
    void handleSchemaIncompatible_withoutSamples_omitsExampleSuffix() {
        var problem = new DossierExceptionHandler().handleSchemaIncompatible(
                new DossierTypeSchemaIncompatibleException(5, List.of()));

        assertThat(problem.getDetail()).contains("5").doesNotContain("(ex.");
    }

    @Test
    void handleSchemaIncompatible_statesTheFullValueCountWhenTheDuplicateListIsCapped() {
        var shown = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> new be.myrmacta.domain.dossier.model.UniqueDuplicate(
                        "plaque", "V" + i, java.util.List.of("A" + i, "B" + i)))
                .toList();
        var problem = new DossierExceptionHandler().handleSchemaIncompatible(
                new DossierTypeSchemaIncompatibleException(42, java.util.List.of(), shown, 21));

        assertThat(problem.getDetail()).contains("21").contains("20 citées");
        assertThat(problem.getProperties()).containsEntry("duplicateCount", 21);
        assertThat(problem.getProperties()).containsEntry("duplicates", shown);
    }
}
