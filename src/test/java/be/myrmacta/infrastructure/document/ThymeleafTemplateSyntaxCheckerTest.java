package be.myrmacta.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.document.model.InvalidTemplateSyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
                    <p th:text="${#dates.format(now, 'dd/MM/yyyy')}">d</p>
                    <p th:text="${#strings.toUpperCase(title)}">t</p>
                    <p th:text="${#numbers.formatInteger(n, 1)}">n</p>
                    <p th:text="${#lists.size(items)}">s</p>
                    <p th:text="${#vars.title}">v</p>
                  </body>
                </html>
                """))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<p th:text=\"${T(java.lang.Runtime).getRuntime()}\">x</p>",
                "<p th:text=\"${#beans}\">x</p>",
                "<p th:text=\"${new java.lang.String('x')}\">x</p>",
                "<p th:text=\"${#request}\">x</p>",
                "<p th:text=\"${title.class}\">x</p>",
                "<p th:text=\"${title.getClass}\">x</p>"
            })
    void expressionsOutsideAllowlist_fail(String html) {
        assertThatThrownBy(() -> checker.check(html)).isInstanceOf(InvalidTemplateSyntaxException.class);
    }

    @Test
    void invalidThymeleafMarkup_fails() {
        assertThatThrownBy(() -> checker.check("<th:block th:each=\"x : ${y}\""))
                .isInstanceOf(InvalidTemplateSyntaxException.class);
    }

    @Test
    void unescapedUtext_fails() {
        assertThatThrownBy(() -> checker.check("<p th:utext=\"${title}\">x</p>"))
                .isInstanceOf(InvalidTemplateSyntaxException.class);
    }

    @Test
    void unescapedInlineInterpolation_fails() {
        assertThatThrownBy(() -> checker.check("<p>[(${title})]</p>"))
                .isInstanceOf(InvalidTemplateSyntaxException.class);
    }

    /** Revue adversariale LC4b (Moyen) : l'interdiction d'interpolation non échappée ne se contourne ni par la casse, ni par la forme HTML5, ni par un blanc. */
    @ParameterizedTest
    @ValueSource(strings = {
        "<p TH:UTEXT=\"${a}\">x</p>",
        "<p data-th-utext=\"${a}\">x</p>",
        "<p>[( ${a} )]</p>",
        "<p>[(\n${a})]</p>",
        "<p th:inline=\"text\">x</p>",
        "<p DATA-TH-INLINE=\"text\">x</p>"
    })
    void unescapedInterpolationIsRefusedWhateverItsSpelling(String html) {
        assertThatThrownBy(() -> checker.check(html)).isInstanceOf(InvalidTemplateSyntaxException.class);
    }

    /** Revue adversariale LC4b, passe 2 (Haut) : un fragment est évalué comme un template — refusé à l'activation. */
    @ParameterizedTest
    @ValueSource(strings = {
        "<p th:replace=\"${x}\">a</p>",
        "<div th:insert=\"~{'<b th:ut' + 'ext=${x}>q</b>'}\">a</div>",
        "<p TH:REPLACE=\"~{'[' + '(${x})]'}\">a</p>",
        "<p data-th-include=\"${x}\">a</p>",
        "<p th:substituteby=\"${x}\">a</p>"
    })
    void fragmentsAreRefused(String html) {
        assertThatThrownBy(() -> checker.check(html)).isInstanceOf(InvalidTemplateSyntaxException.class);
    }

    @Test
    void preProcessing_isRefused_includingWhenWrittenWithEntities() {
        assertThatThrownBy(() -> checker.check("<p th:text=\"__${title}__\">x</p>"))
                .isInstanceOf(InvalidTemplateSyntaxException.class)
                .hasMessageContaining("__");
        assertThatThrownBy(() -> checker.check("<p th:text=\"&#95;&#95;${title}&#95;&#95;\">x</p>"))
                .isInstanceOf(InvalidTemplateSyntaxException.class)
                .hasMessageContaining("__");
    }

    @Test
    void aSignatureLineOfUnderscores_isNotPreProcessing() {
        assertThatCode(() -> checker.check("<p>Signature : ________</p>")).doesNotThrowAnyException();
    }

    /** Gabarits de notification (TODO §123) : objet et corps texte contrôlés en mode TEXT, même grammaire. */
    @Test
    void textMode_acceptsInliningAndConditionals() {
        assertThatCode(() -> checker.check(
                        "Bonjour [[${dossier.label}]]\n[# th:if=\"${variables.x}\"]oui[/]",
                        org.thymeleaf.templatemode.TemplateMode.TEXT))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[[${T(java.lang.Runtime).getRuntime()}]]",
                "[[${#beans.x}]]",
                "[(${dossier.label})]",
                "[( ${dossier.label} )]",
                "[# th:utext=\"${dossier.label}\"]x[/]",
                "[# th:replace=\"${x}\"]x[/]",
                "[[${dossier.label]]",
    })
    void textMode_appliesTheSameRefusals(String text) {
        assertThatThrownBy(() -> checker.check(text, org.thymeleaf.templatemode.TemplateMode.TEXT))
                .isInstanceOf(InvalidTemplateSyntaxException.class);
    }
}
