package com.flowforge.infrastructure.persistence.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.workflow.model.TaskQuestionAnswer;
import com.flowforge.domain.workflow.port.out.TaskQuestionAnswerRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** TODO §68 T-PURGE-1 — suppression réelle des réponses d'un dossier (écart préexistant de la purge §22). */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JpaTaskQuestionAnswerPurgeTest {

    @Autowired
    private TaskQuestionAnswerRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deleteAllByDossierId_removesOnlyThatDossiersAnswers() {
        UUID purged = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        repository.save(answer(purged));
        repository.save(answer(purged));
        repository.save(answer(kept));

        assertThat(repository.deleteAllByDossierId(purged)).isEqualTo(2);

        assertThat(count(purged)).isZero();
        assertThat(count(kept)).isEqualTo(1);
    }

    private int count(UUID dossierId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from task_question_answer where dossier_id = ?", Integer.class, dossierId);
    }

    private static TaskQuestionAnswer answer(UUID dossierId) {
        return new TaskQuestionAnswer(UUID.randomUUID(), "tenant_dev", dossierId, "task-" + System.nanoTime(), "pi",
                "avis", Map.of("disponible", true), "portal:x", Instant.now());
    }
}
