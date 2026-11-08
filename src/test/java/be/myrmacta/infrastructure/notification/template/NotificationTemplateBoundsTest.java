package be.myrmacta.infrastructure.notification.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.document.model.TemplateProcessingException;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import be.myrmacta.infrastructure.config.NotificationTemplateProperties;
import be.myrmacta.infrastructure.document.ThymeleafTemplateSyntaxChecker;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bornes exactes du rendu, du contrôle et de la politique HTML (registre §123) : taille cumulée des parties, octets comptés
 * pendant l'écriture, numéro de ligne des violations, nombre de violations rapportées.
 */
class NotificationTemplateBoundsTest {

    private static NotificationTemplate template(String locale, String subject, String textBody, String htmlBody) {
        Instant now = Instant.parse("2026-11-07T10:00:00Z");
        return new NotificationTemplate(
                UUID.randomUUID(), "bornes", null, locale, "Bornes", 1, TemplateStatus.ACTIVE, subject, textBody, htmlBody,
                "tenant_dev", now, now, "test");
    }

    private static ThymeleafNotificationTemplateRenderer rendererWithMaxBytes(int renderedMaxBytes) {
        return new ThymeleafNotificationTemplateRenderer(
                new NotificationTemplateProperties(0, 0, 0, 0, renderedMaxBytes, 0, 0, 0, 0, 0));
    }

    private static int utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    // ---- rendu : locale, modèle absent, message d'erreur générique ----------------------------------------------------

    @Test
    void render_usesTheLocaleOfTheTemplate() {
        ThymeleafNotificationTemplateRenderer renderer =
                new ThymeleafNotificationTemplateRenderer(NotificationTemplateProperties.defaults());
        Map<String, Object> model = new HashMap<>();
        model.put("d", Date.from(Instant.parse("2026-06-15T12:00:00Z")));

        assertThat(renderer.render(template("fr", "S", "[[${#dates.format(d, 'MMMM')}]]", null), model).textBody())
                .isEqualTo("juin");
        assertThat(renderer.render(template("en", "S", "[[${#dates.format(d, 'MMMM')}]]", null), model).textBody())
                .isEqualTo("June");
    }

    @Test
    void render_withoutALocaleOrAModel_stillRenders() {
        ThymeleafNotificationTemplateRenderer renderer =
                new ThymeleafNotificationTemplateRenderer(NotificationTemplateProperties.defaults());

        RenderedNotification result = renderer.render(template(null, "Objet", "Corps", null), null);

        assertThat(result.subject()).isEqualTo("Objet");
        assertThat(result.textBody()).isEqualTo("Corps");
    }

    @Test
    void render_anErrorOutsideTheEvaluator_getsAGenericMessage() {
        ThymeleafNotificationTemplateRenderer renderer =
                new ThymeleafNotificationTemplateRenderer(NotificationTemplateProperties.defaults());

        assertThatThrownBy(() -> renderer.render(template("fr", "S", "[# th:if=\"${true} ==\"]x[/]", null), Map.of()))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessage("Notification template 'bornes' (textBody): the template could not be evaluated");
    }

    @Test
    void render_anEvaluatorError_namesTheExpressionWithoutTheTemplateLocation() {
        ThymeleafNotificationTemplateRenderer renderer =
                new ThymeleafNotificationTemplateRenderer(NotificationTemplateProperties.defaults());

        assertThatThrownBy(() -> renderer.render(template("fr", "S", "[[${dossier.reference}]]", null), Map.of()))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessage("Notification template 'bornes' (textBody): Unknown template variable: dossier");
    }

    // ---- rendu : taille cumulée des parties nettoyées ------------------------------------------------------------------

    @Test
    void render_theCleanedPartsCount_towardsTheRenderedLimit() {
        // Le nettoyage ajoute <html><head>…<body> : la somme finale dépasse celle de l'écriture.
        String html = "<p>x</p>";
        int cleaned = utf8(NotificationHtmlPolicy.clean(html));
        assertThat(cleaned).isGreaterThan(html.length());
        int exact = utf8("é") + utf8("T") + cleaned;

        RenderedNotification result = rendererWithMaxBytes(exact).render(template("fr", "é", "T", html), Map.of());
        assertThat(result.htmlBody()).isEqualTo(NotificationHtmlPolicy.clean(html));

        assertThatThrownBy(() -> rendererWithMaxBytes(exact - 1).render(template("fr", "é", "T", html), Map.of()))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessage("Notification template 'bornes' (rendering): the rendered message exceeds " + (exact - 1) + " bytes");
    }

    @Test
    void render_withoutHtml_theTotalIsTheSubjectAndTheText() {
        assertThat(rendererWithMaxBytes(2 + 3).render(template("fr", "é", "abc", null), Map.of()).textBody())
                .isEqualTo("abc");
        assertThatThrownBy(() -> rendererWithMaxBytes(2 + 3 - 1).render(template("fr", "é", "abc", null), Map.of()))
                .isInstanceOf(TemplateProcessingException.class);
    }

    // ---- rendu : octets comptés pendant l'écriture ----------------------------------------------------------------------

    /**
     * Le texte « &amp;amp; » est écrit tel quel (5 octets) puis défait en « &amp; » (1 octet) : seule la borne appliquée
     * pendant l'écriture distingue la taille écrite de la taille finale.
     */
    private void assertWrittenBytes(String chars, int expectedBytes) {
        int amplified = 10;
        String body = "&amp;".repeat(amplified) + chars;
        int written = 1 + 5 * amplified + expectedBytes;

        RenderedNotification result = rendererWithMaxBytes(written).render(template("fr", "S", body, null), Map.of());
        assertThat(result.textBody()).isEqualTo("&".repeat(amplified) + chars);

        assertThatThrownBy(() -> rendererWithMaxBytes(written - 1).render(template("fr", "S", body, null), Map.of()))
                .as("%d octets pour %s", expectedBytes, chars)
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessage("Notification template 'bornes' (textBody): the rendered message exceeds " + (written - 1) + " bytes");
    }

    @Test
    void write_countsOneByteForAscii() {
        assertWrittenBytes("abc", 3);
    }

    @Test
    void write_countsTwoBytesFromU0080() {
        assertWrittenBytes("\u0080", 2);
        assertWrittenBytes("é", 2);
        assertWrittenBytes("߿", 2);
    }

    @Test
    void write_countsThreeBytesFromU0800() {
        assertWrittenBytes("ࠀ", 3);
        assertWrittenBytes("€", 3);
    }

    @Test
    void write_countsFourBytesForASurrogatePair() {
        assertWrittenBytes("😀", 4);
    }

    // ---- contrôle : nombre de violations rapportées ----------------------------------------------------------------------

    private final ThymeleafNotificationTemplateChecker checker =
            new ThymeleafNotificationTemplateChecker(new ThymeleafTemplateSyntaxChecker());

    private static String images(int count) {
        return "<img>\n".repeat(count);
    }

    @Test
    void check_reportsTwentyViolationsWithoutATrailer_andCountsTheRest() {
        assertThatThrownBy(() -> checker.check(template(null, "S", "T", images(20))))
                .isInstanceOfSatisfying(NotificationTemplateValidationException.class, e -> {
                    assertThat(e.part()).isEqualTo("htmlBody");
                    assertThat(e.getMessage()).doesNotContain("more").contains("(line 20)");
                });
        assertThatThrownBy(() -> checker.check(template(null, "S", "T", images(21))))
                .isInstanceOfSatisfying(NotificationTemplateValidationException.class, e -> {
                    assertThat(e.getMessage()).endsWith("; and 1 more").doesNotContain("(line 21)");
                });
        assertThatThrownBy(() -> checker.check(template(null, "S", "T", images(25))))
                .isInstanceOfSatisfying(NotificationTemplateValidationException.class,
                        e -> assertThat(e.getMessage()).endsWith("; and 5 more"));
    }

    // ---- politique HTML ------------------------------------------------------------------------------------------------------

    @Test
    void violations_nameTheLineOfAThymeleafAttribute() {
        assertThat(NotificationHtmlPolicy.violations("<p th:utext=\"x\">c</p>"))
                .anyMatch(v -> v.contains("th:utext") && v.endsWith("at line 1"));
        assertThat(NotificationHtmlPolicy.violations("<p>a</p>\n<p>b</p>\n<p th:utext=\"x\">c</p>"))
                .anyMatch(v -> v.contains("th:utext") && v.endsWith("at line 3"));
        assertThat(NotificationHtmlPolicy.violations("<p>a</p>\n\n\n\n<p data-th-inline=\"text\">c</p>"))
                .anyMatch(v -> v.contains("data-th-inline") && v.endsWith("at line 5"));
    }

    @Test
    void aStyleWithABackslash_isNeverSafe_whereverTheBackslashIs() {
        assertThat(NotificationHtmlPolicy.isSafeStyle("\\75rl(x)")).isFalse();
        assertThat(NotificationHtmlPolicy.isSafeStyle("\\")).isFalse();
        assertThat(NotificationHtmlPolicy.isSafeStyle("color: re\\64")).isFalse();
        assertThat(NotificationHtmlPolicy.isSafeStyle("color: red")).isTrue();
    }

    @Test
    void check_aCleanTemplate_isStillAccepted() {
        assertThatCode(() -> checker.check(template(null, "S", "T", images(0) + "<p>ok</p>"))).doesNotThrowAnyException();
    }
}
