package be.myrmacta.infrastructure.notification.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import be.myrmacta.infrastructure.document.ThymeleafTemplateSyntaxChecker;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ThymeleafNotificationTemplateCheckerTest {

    private final ThymeleafNotificationTemplateChecker checker =
            new ThymeleafNotificationTemplateChecker(new ThymeleafTemplateSyntaxChecker());

    private static NotificationTemplate template(String subject, String textBody, String htmlBody) {
        Instant now = Instant.parse("2026-11-07T10:00:00Z");
        return new NotificationTemplate(
                UUID.randomUUID(), "k", null, null, "L", 1, TemplateStatus.DRAFT, subject, textBody, htmlBody, "tenant_dev",
                now, now, "test");
    }

    private void assertRefused(NotificationTemplate template, String part, String... messageParts) {
        assertThatThrownBy(() -> checker.check(template))
                .isInstanceOfSatisfying(NotificationTemplateValidationException.class, e -> {
                    assertThat(e.part()).isEqualTo(part);
                    for (String expected : messageParts) {
                        assertThat(e.getMessage()).contains(expected);
                    }
                });
    }

    @Test
    void aWellFormedTemplate_isAccepted() {
        assertThatCode(() -> checker.check(template(
                        "Accusé : [[${dossier.fields.objet}]]",
                        "Dossier [[${dossier.reference}]]\n[# th:if=\"${variables.motivation}\"]Motif : [[${variables.motivation}]][/]",
                        "<p>Dossier <b th:text=\"${dossier.reference}\">REF</b></p>")))
                .doesNotThrowAnyException();
    }

    @Test
    void theHtmlPartIsOptional() {
        assertThatCode(() -> checker.check(template("S", "T", null))).doesNotThrowAnyException();
    }

    @Test
    void anExpressionOutsideTheGrammar_isRefused_inEachTextPart() {
        assertRefused(template("[[${T(java.lang.Runtime).getRuntime()}]]", "T", null), "subject", "T(java.lang.Runtime)");
        assertRefused(template("S", "[[${new java.util.Date()}]]", null), "textBody");
        assertRefused(template("S", "T", "<p th:text=\"${#beans.foo}\">x</p>"), "htmlBody");
    }

    @Test
    void unescapedInterpolation_isRefused_inTextToo() {
        assertRefused(template("S", "Bonjour [(${dossier.label})]", null), "textBody", "Unescaped");
        assertRefused(template("[( ${dossier.label} )]", "T", null), "subject", "Unescaped");
        assertRefused(template("S", "T", "<p th:utext=\"${dossier.label}\">x</p>"), "htmlBody", "Unescaped");
    }

    @Test
    void fragments_areRefused() {
        assertRefused(template("S", "T", "<div th:replace=\"x\">y</div>"), "htmlBody", "fragments");
    }

    @Test
    void anUnclosedExpression_isRefused() {
        assertRefused(template("S", "Bonjour [[${dossier.label]]", null), "textBody");
    }

    @Test
    void theHtmlPolicy_refusesWithTheRuleViolated() {
        assertRefused(template("S", "T", "<p>x</p><img src=\"https://t.example/p.gif\">"), "htmlBody", "<img>", "not allowed");
        assertRefused(template("S", "T", "<p onclick=\"x()\">x</p>"), "htmlBody", "onclick");
        assertRefused(template("S", "T", "<p style=\"background:url(x)\">x</p>"), "htmlBody", "style");
        assertRefused(template("S", "T", "<a th:href=\"${dossier.label}\">x</a>"), "htmlBody", "th:href");
        assertRefused(template("S", "T", "<a href=\"javascript:alert(1)\">x</a>"), "htmlBody", "href");
        assertRefused(template("S", "T", "<script>x</script>"), "htmlBody", "<script>");
    }

    @Test
    void theViolationList_isCapped() {
        String many = "<img>".repeat(100);

        assertThatThrownBy(() -> checker.check(template("S", "T", many)))
                .isInstanceOfSatisfying(NotificationTemplateValidationException.class, e -> {
                    assertThat(e.getMessage()).contains("and 80 more");
                    assertThat(e.getMessage().length()).isLessThan(2_000);
                });
    }

    @Test
    void theHtmlPolicyIsNotAppliedToTextParts() {
        assertThatCode(() -> checker.check(template("Re: <b>urgent</b>", "<img> et onclick= sont du texte", null)))
                .doesNotThrowAnyException();
    }

    @Test
    void preProcessing_isRefused_inEveryPart() {
        assertRefused(template("S", "[[__${a}__]]", null), "textBody", "__");
        assertRefused(template("S", "[# th:text=\"${__${a}__}\"][/]", null), "textBody", "__");
        assertRefused(template("a __${a}__", "T", null), "subject", "__");
        assertRefused(template("S", "T", "<p th:text=\"__${a}__\">x</p>"), "htmlBody", "__");
        assertRefused(template("S", "T", "<p th:text=\"${__${a}__}\">x</p>"), "htmlBody", "__");
    }

    @Test
    void preProcessingWrittenWithHtmlEntities_isRefused() {
        for (String underscores : java.util.List.of("&#95;&#95;", "&#x5f;&#x5F;", "&lowbar;&lowbar;", "&UnderBar;&UnderBar;")) {
            String expr = underscores + "${a}" + underscores;
            assertRefused(template("S", "T", "<p th:text=\"" + expr + "\">x</p>"), "htmlBody", "pre-processing");
            assertRefused(template("S", "T", "<p>[[" + expr + "]]</p>"), "htmlBody", "pre-processing");
            assertRefused(template("S", "[[" + expr + "]]", null), "textBody", "pre-processing");
            assertRefused(template("S", "[# th:text=\"" + expr + "\"][/]", null), "textBody", "pre-processing");
            assertRefused(template(expr, "T", null), "subject", "pre-processing");
        }
    }

    @Test
    void expressionsWrittenWithEntities_areSubjectToTheGrammarCheck() {
        assertRefused(template("S", "T", "<p th:text=\"&#36;{variables.zzz.getClass}\">x</p>"), "htmlBody");
        assertRefused(template("S", "[[&#36;{variables.zzz.getClass}]]", null), "textBody");
    }
}
