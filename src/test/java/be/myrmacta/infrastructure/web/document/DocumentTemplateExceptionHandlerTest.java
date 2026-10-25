package be.myrmacta.infrastructure.web.document;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.document.model.InvalidTemplateStateException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class DocumentTemplateExceptionHandlerTest {

    private final DocumentTemplateExceptionHandler handler = new DocumentTemplateExceptionHandler();

    @Test
    void dataIntegrity_doesNotLeakSqlDetail() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("Unique index uq_template_active ON document_template(...)"));
        ProblemDetail problem = handler.handleIntegrity(ex);
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).contains("ACTIVE template already exists");
        assertThat(problem.getDetail()).doesNotContain("ON document_template");
    }

    @Test
    void dataIntegrity_versionConflict_isSanitized() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "conflict", new RuntimeException("duplicate key uq_template_version"));
        ProblemDetail problem = handler.handleIntegrity(ex);
        assertThat(problem.getDetail()).contains("version already exists");
    }

    @Test
    void invalidState_keepsDomainMessage() {
        ProblemDetail problem =
                handler.handleInvalidState(new InvalidTemplateStateException("Only DRAFT templates can be activated"));
        assertThat(problem.getDetail()).isEqualTo("Only DRAFT templates can be activated");
    }
}
