package com.flowforge.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.document.model.InvalidTemplateSyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThymeleafTemplateSyntaxCheckerTest {

    private ThymeleafTemplateSyntaxChecker checker;

    @BeforeEach
    void setUp() {
        checker = new ThymeleafTemplateSyntaxChecker();
    }

    @Test
    void validThymeleafMarkup_passes() {
        assertThatCode(() -> checker.check("<p th:text=\"${title}\">x</p>")).doesNotThrowAnyException();
    }

    @Test
    void missingBusinessVariables_doNotFail_syntaxOnly() {
        assertThatCode(() -> checker.check("""
                <html>
                  <body>
                    <h1 th:text="${dossier.reference}">ref</h1>
                    <p th:each="line : ${lines}" th:text="${line}">l</p>
                  </body>
                </html>
                """))
                .doesNotThrowAnyException();
    }

    @Test
    void invalidThymeleafMarkup_fails() {
        assertThatThrownBy(() -> checker.check("<th:block th:each=\"x : ${y}\""))
                .isInstanceOf(InvalidTemplateSyntaxException.class);
    }
}
